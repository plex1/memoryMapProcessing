package mmProcessing

// mmProcessing extract the info from BusSlaveFactory into a data interchage format.
// Supported are: Cheby, JSON and YAML
//
// To make the most of this tool the documentation field of the BusSlaveFactory should be formatted
// with a specific structure. mmProcessing will parse the documentation of it complies with this structure.
//
// Format of the BusSlaveFactory documentation argument:
// Valid Formats: "RegName | FieldName | FieldDescription | FieldComment"
//                "RegName | FieldName | FieldDescription"
//                "| FieldName | FormatDescription"      (FieldName will be used as RegName
//
//  Definitions (from Cheby):
// - name: The name of the node. This is required for all nodes (registers, fields)
// - description: This should be a short text explain the purpose of the node.
//     This attribute is not required but it is recommended to always provide it.
// - comment: This is a longer or more detailed text that will be copied into the
//     generated documentation. New lines are allowed with \n
//
//  see MMProcessingExample.scala for a sample code


import io.circe._, io.circe.generic.auto._, io.circe.parser._, io.circe.syntax._, io.circe.Printer
import io.circe.yaml._, io.circe.yaml.syntax._

import java.io.{File, PrintWriter}

import spinal.lib.bus.misc.BusSlaveFactoryRead
import spinal.lib.bus.misc._

import scala.collection.mutable.ListBuffer

// definition of access format
case class Access(read: Boolean, write: Boolean) {
  override def toString(): String = {
    if (read && !write) "ro"
    else if (!read && write) "wo"
    else if (read && write) "rw"
    else ""
  }

  // combine several access rights of several data enities is done OR logic
  def orCombine(other: Access): Access = {
    Access(this.read || other.read, this.write || other.write)
  }
}

// cheby data strucutre definition
case class ChebyDefinition(`memory-map`: MemoryMap)
case class MemoryMap(bus: String, name: String, description: String, children: List[RegChildren])
case class RegChildren(reg: RegCheby)
case class RegCheby(name: String, width: Int, access: String, children: List[FieldChildren])
case class FieldChildren(field : FieldCheby)
case class FieldCheby(name: String, description: String, range: String)

// MMProcessing data structure definition
case class Field(bitOffset: Int, bitWidth: Int, name: String, description: String, comment: String = "", access: Access)
case class Register(address: BigInt, name: String, fields : List[Field], access: Access)
case class MMpDefinition(name: String, description: String, offset: BigInt = 0, registers: List[Register])

// Abstract definition of format to generate files with any format
abstract class MMStrings(){
  def addHeaderString(builder: StringBuilder, name: String, description: String)
  def addRegisterStartString(builder: StringBuilder, register: Register)
  def addRegisterEndString(builder: StringBuilder, register: Register)
  def addFieldString(builder: StringBuilder, field: Field)
  def addFooterString(builder: StringBuilder)
  def addFieldsStartString(builder: StringBuilder, register: Register)
  def addFieldsEndString(builder: StringBuilder, register: Register)
}

// Definition of format to generate files with Cheby format
case class MMChebyStrings(reg_width : Int) extends MMStrings{

  def addHeaderString(builder: StringBuilder, name: String = "None", description: String = "None") = {
    builder ++= s"#  Configuration and status register definitions for ${name}\n"
    builder ++= s"memory-map:\n"
    builder ++= s" bus: wb-32-be\n"
    builder ++= s" name: ${name}\n"
    builder ++= s" description: ${'"'}${description}${'"'}\n"
    builder ++= s" children:\n"

  }

  def addRegisterStartString(builder: StringBuilder, register: Register): Unit ={
    builder ++= s"  - reg:\n"
    builder ++= s"      name: ${register.name}\n"
    builder ++= s"      address: ${register.address}\n"
    builder ++= s"      width: ${reg_width}\n"
    builder ++= s"      access: ${register.access}\n"

  }

  def addRegisterEndString(builder: StringBuilder, register: Register): Unit ={}

  def addFieldsStartString(builder: StringBuilder, register: Register): Unit = {
    builder ++= s"      children:\n"
  }

  def addFieldsEndString(builder: StringBuilder, register: Register): Unit = {}

  def addFieldString(builder: StringBuilder, field: Field): Unit = {
    builder ++= s"        - field:\n"
    builder ++= s"            name: ${field.name}\n"
    builder ++= s"            description: ${'"'}${field.description}${'"'}\n"
    val range = if (field.bitWidth==1) s"${field.bitOffset}" else
      s"${field.bitWidth+field.bitOffset-1}-${field.bitOffset}"
    builder ++= s"            range: ${range}\n"
    if (field.comment != "") {
      builder ++= s"            comment: ${'"'}${
        field.comment.replace("\n", "\\n")
      }${'"'}\n"
    }
  }

  def addFooterString(builder: StringBuilder){}
}

// definition of format on how to extract fields from BusSlaveFactory documentation argument
object DocumentationFormat {

  case class DocumentationParts(RegName : String, FieldName: String, FieldDescription: String, FieldComment: String = "")

  def extract_docu(documentation: String) : Option[DocumentationParts] = {
    if (documentation != null) {
      val b = documentation.split('|').map(_.trim)
      if (b != null && b.length>=3) {
        if (b.length==3)
          Some(DocumentationParts(b(0), b(1), b(2)))
        else
          Some(DocumentationParts(b(0), b(1), b(2), b(3)))
      } else None
    } else None
  }

}

// config of MMProcessing
case class MMProcessingConfig(name: String = "None", description: String = "None", offset: BigInt = 0, addr_inc: Int = 4,
                               fill_gaps: Boolean = true, nofield_keep : Boolean = false, add_nodocu: Boolean = true)

// main class MMprocessing
class MMProcessing(busCtrl: BusSlaveFactoryDelayed, config : MMProcessingConfig) {

  val reg_width = 32


  // helper function
  def addRegisterString(mmStrings: MMStrings, builder: StringBuilder, register: Register): Unit = {

    mmStrings.addRegisterStartString(builder, register)

    if (register.fields.nonEmpty || config.nofield_keep) mmStrings.addFieldsStartString(builder, register)

    // add fields
    for (field <- register.fields) {
      // add field
      mmStrings.addFieldString(builder, field)
    }

    if (register.fields.nonEmpty || config.nofield_keep) mmStrings.addFieldsEndString(builder, register)
    mmStrings.addRegisterEndString(builder, register)
  }

  // extract list of registers from busCtrl instance
  def extractRegisters: List[Register] = {

    // convert to list of Register
    val regs = (for ((address, jobs) <- busCtrl.elementsPerAddress.toList.sortBy(_._1.lowerBound)) yield {

      var regName: String = ""

      // convert to list of Field
      val fields = (for (job <- jobs.filter(j => j.isInstanceOf[BusSlaveFactoryRead] || j.isInstanceOf[BusSlaveFactoryWrite]))
        yield job match {
          case job: BusSlaveFactoryRead =>
            DocumentationFormat.extract_docu(job.documentation) match {
              case Some(docu) =>
                if (docu.RegName.length() > 0) regName = docu.RegName
                Some(Field(job.bitOffset, job.that.getBitsWidth, docu.FieldName, docu.FieldDescription, docu.FieldComment, Access(true, false)))
              case None =>
                if (config.add_nodocu) Some(Field(job.bitOffset, job.that.getBitsWidth,
                  if (job.that.getName() == "") s"NoFieldName${job.bitOffset}" else job.that.getName(), job.documentation, "", Access(true, false)))
                else None
            }
          case job: BusSlaveFactoryWrite =>
            DocumentationFormat.extract_docu(job.documentation) match {
              case Some(docu) =>
                if (docu.RegName.length() > 0) regName = docu.RegName
                Some(Field(job.bitOffset, job.that.getBitsWidth, docu.FieldName, docu.FieldDescription, docu.FieldComment, Access(true, true)))
              case None =>
                if (config.add_nodocu) Some(Field(job.bitOffset, job.that.getBitsWidth,
                  if (job.that.getName() == "") s"NoFieldName${job.bitOffset}" else job.that.getName(), job.documentation, "", Access(true, true)))
                else None
            }
          case _ => None
        }).toList.flatten

      // combine read and write fields
      val fields_comb = fields.groupBy(_.bitOffset).map {
        case (k, v) =>
          if (v.size > 1)
            v.head.copy(access = v.head.access orCombine v(1).access)
          else v.head
      }.toList.sortBy(_.bitOffset)


      // generate Register instance or None
      if (fields_comb.nonEmpty) {
        // find a valid register name if there is none
        if (regName == "") regName = fields_comb.head.name
        if (regName == "NoFieldName" || regName == "" || (regName contains "NoFieldName")) regName = s"NoRegName${address.lowerBound.toInt}"

        Some(Register(address.lowerBound, regName, fields_comb,
          if (fields_comb.nonEmpty)
            fields_comb.map(_.access).reduceLeft(_ orCombine _)
          else Access(true, false)))
      }
      else
        None

    }).flatten //flatten removes None Option from list

    regs

  }

  // generate cheby file string
  def chebyFileString(regs: List[Register]): String = {

    // Construct Cheby string
    val chebyBuilder = new StringBuilder()
    val mmStrings = MMChebyStrings(reg_width)
    mmStrings.addHeaderString(chebyBuilder, config.name , config.description)
    for (reg <- regs) {
      if (reg.fields.nonEmpty)
        addRegisterString(mmStrings, chebyBuilder, Register(reg.address, reg.name, reg.fields, reg.fields(0).access))
    }
    mmStrings.addFooterString(chebyBuilder)

    chebyBuilder.toString

  }

  // genrate json or yaml string for mmProcessing data structure
  def mmProcessingFormatFileString(regs: List[Register], outputFormat: String): String = {

    // Construct MMProcessing data structure
    val mmpDefintion = MMpDefinition(config.name, config.description, config.offset, regs)

    // serialize case class
    outputFormat match {
      case "json_mmp" => mmpDefintion.asJson.pretty(Printer.indented("  "))
      case "yaml_mmp" => mmpDefintion.asJson.asYaml.spaces4
      case _ =>  ""
    }
  }

  // second implementation to generate yaml file with cheby format
  // currently not accepted by the cheby tools due to range field.
  // yaml has format> range: '7-0', range: '0'
  // cheby has format> range 7-0, range: 0
  def chebyFormatFileString(regs: List[Register], outputFormat: String): String = {

    // Construct Cheby data structure
    val regChildren = for (reg <- regs) yield {
      val fieldChildren = for (field <- reg.fields) yield {
        val range = if (field.bitWidth==1) s"${field.bitOffset}" else s"${field.bitWidth+field.bitOffset-1}-${field.bitOffset}"
        val fieldCheby = FieldCheby(field.name, field.description, range)
        FieldChildren(fieldCheby)
      }
      RegChildren(RegCheby(reg.name, 32, reg.access.toString(), fieldChildren))
    }

    val chebyDefinition = ChebyDefinition(MemoryMap("wb-32-be", config.name, config.description, regChildren))

    // serialize case class
    outputFormat match {
      case "json_cheby" => chebyDefinition.asJson.pretty(Printer.indented("  "))//mmDefintion.asJson.noSpaces
      case "yaml_cheby" => chebyDefinition.asJson.asYaml.spaces4
      case _ =>  ""
    }

  }


  def mmFileString(regs: List[Register], outputFormat: String): String = {

    outputFormat match {
      case "json_mmp" | "yaml_mmp" => mmProcessingFormatFileString(regs, outputFormat)
      case "json_cheby" | "yaml_cheby" => chebyFormatFileString(regs, outputFormat)
      case "cheby" => chebyFileString(regs)
      case _ =>  ""
    }
  }


  def writeMMFile(filename:String, outputFormat: String = "json"): Unit ={
    val str =  mmFileString(extractRegisters, outputFormat)
    val pw = new PrintWriter(new File(filename ))
    pw.write(str)
    pw.close()
  }
}
