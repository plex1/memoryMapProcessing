package mmProcessing


import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba3.apb.{Apb3, Apb3SlaveFactory}


//Hardware definition
class MotorControlPeripheral extends Component {
  val io = new Bundle {
    val apb = slave(Apb3(
      addressWidth = 8,
      dataWidth = 32
    ))
    val testLed = out Bool
    val endStop1 = in Bool
    val endStop2 = in Bool
    val positionPoti = in UInt(10 bits)
  }

  object driveMode extends SpinalEnum() {
    val NORMAL , SLOW, FAST = newElement()
  }

  val busCtrl = Apb3SlaveFactory(io.apb)

  // define the bus configuration and status registers

  val target_posx = busCtrl.createReadAndWrite(UInt(16 bits), 0x00, 0,
    "| pos_x | target position for x motor | position in number of steps\n Range: 0-9999") init (0)
  val traget_posy = busCtrl.createReadAndWrite(UInt(16 bits), 0x04, 0,
    "| pos_y | target position for y motor | position in number of steps\n Range: 0-9999") init (0)

  val drive_mode = busCtrl.createReadAndWrite(driveMode(), 0x08, 0,
    "control | drive_mode | driving mode | 0: NORMAL\n1: SLOW\n3: READ") init (driveMode.NORMAL)

  val ignore_es = busCtrl.createReadAndWrite(UInt(1 bits), 0x08, 2,
    "control | ignore_es | ignore end switch | 0: do not ignore\n1: ignore") init (0)

  busCtrl.driveAndRead(io.testLed,0x8,6, "control | test_led | test led | 0: off\n1: on")

  busCtrl.read(io.endStop1, 0xc, 16, "position | end_switch_1 | end switch 1 status | 0: off\n1: on")
  busCtrl.read(io.endStop2, 0xc, 17, "position | end_switch_2 | end switch 2 status | 0: off\n1: on")
  busCtrl.read(io.positionPoti, 0xc, 0, "position | potentiometer | potentiometer value in raw ADC data (LSB)| Range: 0-1023")

  busCtrl.read(U"h1a", 0x14, 0, "| id | ID of this module (default: 0x1a)")

  // custom logic
  // here comes the motor controller logic



  // generate documentation output
  val peripheral_apb3_offset = 0xF0030000l
  val mmp = new MMProcessing(busCtrl, MMProcessingConfig("MotorControlPeripheral",
    "APB 3 Peripheral which implements a motor controller", peripheral_apb3_offset))
  mmp.writeMMFile("MotorControlPeripheral.cheby", "cheby")
  mmp.writeMMFile("MotorControlPeripheral.json", "json_mmp")

}

//Generate the MyTopLevel's Verilog
object MyTopLevelVerilog {
  def main(args: Array[String]) {
    SpinalVerilog(new MotorControlPeripheral)
  }
}

//Generate the MyTopLevel's VHDL
object MyTopLevelVhdl {
  def main(args: Array[String]) {
    SpinalVhdl(new MotorControlPeripheral)
  }
}


//Define a custom SpinalHDL configuration with synchronous reset instead of the default asynchronous one. This configuration can be resued everywhere
object MySpinalConfig extends SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC))

//Generate the MyTopLevel's Verilog using the above custom configuration.
object MyTopLevelVerilogWithCustomConfig {
  def main(args: Array[String]) {
    MySpinalConfig.generateVerilog(new MotorControlPeripheral)
  }
}
