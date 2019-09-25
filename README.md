# CsrProcessing


## Description

The CsrProcessing scala code extracts the info from BusSlaveFactory and transforms it to a data interchage format. Supported are: [Cheby](https://gitlab.cern.ch/cohtdrivers/cheby) , JSON and YAML.

To make the most of this tool, the *documentation* field of the BusSlaveFactory should be formatted
with a specific structure. CsrProcessing will parse the documentation if it complies with this format.

## Format

Valid formats for the BusSlaveFactory *documentation* argument: 
- "RegName | FieldName | FieldDescription | FieldComment"
- "RegName | FieldName | FieldDescription"   (Comment will be empty)
- "| FieldName | FormatDescription"          (FieldName will be used as RegName)

Definitions (from Cheby):
 - **name:** The name of the node. This is required for all nodes (registers, fields)
 - **description:** This should be a short text explain the purpose of the node.
    This attribute is not required but it is recommended to always provide it.
 - **comment:** This is a longer or more detailed text that will be copied into the
     generated documentation. New lines are allowed here by using ```\n```.

see ```CcsrProcessingExample.scala``` for a sample code.

## Usage

Run the SpinalHDL synthesis:

```sh
cd CsrProcessing

//Generate verilog and CsrProcessing output (Cheby and JSON)
sbt "runMain csrProcessing.MyTopLevelVerilog"

```

These commands will generate the output files: ```MyTopLevel.v```, ```MotorControlPeripheral.cheby``` and ```MotorControlPeripheral.json```.

We can now use [Cheby](https://gitlab.cern.ch/cohtdrivers/cheby) to auto-genrate documenation files.

```sh
# Generate html documentation
cheby --doc=html --gen-doc=mcp.html -i MotorControlPeripheral.cheby

#Generate c header
cheby --gen-c mcp.h -i MotorControlPeripheral.cheby
```

Pre-generated files can be found here: [mcp.html](mcp.html) and [mcp.h](mcp.h)




