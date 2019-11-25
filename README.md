# Memory Map Processing (MMProcessing) for Spinal HDL


## Description

The mmProcessing package *(mm=memory map)* is a tool for [SpinalHDL](https://github.com/SpinalHDL/SpinalHDL). It can extract the memory map from a SpinalHDL BusSlaveFactory instance and transform it to a data interchange format. Supported are: [Cheby](https://gitlab.cern.ch/cohtdrivers/cheby) , JSON and YAML. With this flow automated documentation or integration of the memory map into other programs or programming languages are easily possible.

To make the most of this tool, the *documentation* field of the BusSlaveFactory should have a specific format. MMProcessing will parse the *documentation* field if it complies with this format.

## Format

Valid formats for the BusSlaveFactory *documentation* argument are:
- "RegName | FieldName | FieldDescription | FieldComment"
- "RegName | FieldName | FieldDescription"        (Comment will be empty)
- "| FieldName | FieldDescription"                (FieldName will be used as the RegName)
- "| FieldName | FieldDescription | FieldComment" (FieldName will be used as the RegName)

Definitions (from Cheby):
 - **name:** The name of the node. This is required for all nodes (registers, fields)
 - **description:** This should be a short text explain the purpose of the node.
    This attribute is not required but it is recommended to always provide it.
 - **comment:** This is a longer or more detailed text that will be copied into the
     generated documentation. New lines are allowed here by using ```\n```.

See [MMProcessingExample.scala](src/main/scala/mmProcessing/MMProcessingExample.scala) for a sample code.

## Usage

Run the SpinalHDL synthesis:

```sh
cd MMProcessing

//Generate verilog and MMProcessing output (Cheby and JSON)
sbt "runMain mmProcessing.MyTopLevelVerilog"

```

These commands will generate the output files: ```MyTopLevel.v```, ```MotorControlPeripheral.cheby``` and ```MotorControlPeripheral.json```.

We can now use [Cheby](https://gitlab.cern.ch/cohtdrivers/cheby) to auto-generate documentation files.

```sh
# Generate html documentation
cheby --doc=html --gen-doc=mcp.html -i MotorControlPeripheral.cheby

# Generate c header
cheby --gen-c mcp.h -i MotorControlPeripheral.cheby
```

Pre-generated files can be found here: [mcp.html](http://htmlpreview.github.io/?https://github.com/plex1/MMProcessing/blob/master/mcp.html) and [mcp.h](mcp.h)




