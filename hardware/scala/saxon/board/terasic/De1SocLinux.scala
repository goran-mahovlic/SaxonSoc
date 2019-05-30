package saxon.board.terasic

import saxon.ResetSourceKind.EXTERNAL
import saxon._
import spinal.core._
import spinal.lib.com.jtag.sim.JtagTcp
import spinal.lib.com.uart.UartCtrlMemoryMappedConfig
import spinal.lib.com.uart.sim.{UartDecoder, UartEncoder}
import spinal.lib.generator._
import spinal.lib.io.{Gpio, InOutWrapper}
import spinal.lib.memory.sdram.IS42x320D
import spinal.lib.memory.sdram.sim.SdramModel




class De1SocLinuxSystem extends SaxonSocLinux{
  //Add components
  val sdramA = SdramSdrBmbGenerator(0x80000000l)
  val gpioA = Apb3GpioGenerator(0x00000)

  //Interconnect specification
  interconnect.addConnection(
    cpu.iBus -> List(sdramA.bmb),
    cpu.dBus -> List(sdramA.bmb)
  )
}

class De1SocLinux extends Generator{
  val clockCtrl = ClockDomainGenerator()
  clockCtrl.resetSourceKind.load(EXTERNAL)
  clockCtrl.powerOnReset.load(true)

  val system = new De1SocLinuxSystem()
  system.onClockDomain(clockCtrl.clockDomain)

  val clocking = add task new Area{
    val CLOCK_50 = in Bool()
    val resetN = in Bool()
    val sdramClk = out Bool()

    val pll = De1SocLinuxPll()
    pll.refclk := CLOCK_50
    pll.rst := False
    sdramClk := pll.outclk_1
    clockCtrl.clock.load(pll.outclk_0)
    clockCtrl.reset.load(!resetN)
  }
}

case class De1SocLinuxPll() extends BlackBox{
  setDefinitionName("pll_0002")
  val refclk = in Bool()
  val rst = in Bool()
  val outclk_0 = out Bool()
  val outclk_1 = out Bool()
  val outclk_2 = out Bool()
  val outclk_3 = out Bool()
  val locked = out Bool()
}


object De1SocLinuxSystem{
  def default(g : De1SocLinuxSystem, clockCtrl : ClockDomainGenerator) = g {
    import g._

    cpu.config.load(VexRiscvConfigs.linux)
    cpu.enableJtag(clockCtrl)

    sdramA.layout.load(IS42x320D.layout)
    sdramA.timings.load(IS42x320D.timingGrade7)

    uartA.parameter load UartCtrlMemoryMappedConfig(
      baudrate = 1000000,
      txFifoDepth = 128,
      rxFifoDepth = 128
    )

    gpioA.parameter load Gpio.Parameter(
      width = 8,
      interrupt = List(0, 1)
    )

    plic.addInterrupt(source = gpioA.produce(gpioA.logic.io.interrupt(0)), id = 4)
    plic.addInterrupt(source = gpioA.produce(gpioA.logic.io.interrupt(1)), id = 5)

    g
  }
}

object De1SocLinux {
  //Function used to configure the SoC
  def default(g : De1SocLinux) = g{
    import g._
    clockCtrl.clkFrequency.load(100 MHz)
    De1SocLinuxSystem.default(system, clockCtrl)
    g
  }

  //Generate the SoC
  def main(args: Array[String]): Unit = {
    SpinalRtlConfig.generateVerilog(InOutWrapper(default(new De1SocLinux()).toComponent()))
  }
}




object De1SocLinuxSystemSim {
  import spinal.core.sim._

  def main(args: Array[String]): Unit = {

    val simConfig = SimConfig
    simConfig.allOptimisation
    simConfig.withWave
    simConfig.compile(new De1SocLinuxSystem(){
      val clockCtrl = ClockDomainGenerator()
      this.onClockDomain(clockCtrl.clockDomain)
      clockCtrl.makeExternal()
      clockCtrl.powerOnReset.load(true)
      clockCtrl.clkFrequency.load(100 MHz)
      De1SocLinuxSystem.default(this, clockCtrl)
    }.toComponent()).doSimUntilVoid("test", 42){dut =>
      val systemClkPeriod = (1e12/dut.clockCtrl.clkFrequency.toDouble).toLong
      val jtagClkPeriod = systemClkPeriod*4
      val uartBaudRate = 1000000
      val uartBaudPeriod = (1e12/uartBaudRate).toLong

      val clockDomain = ClockDomain(dut.clockCtrl.clock, dut.clockCtrl.reset)
      clockDomain.forkStimulus(systemClkPeriod)
//      clockDomain.forkSimSpeedPrinter(4)

//      fork{
//        while(true){
//          sleep(systemClkPeriod*1000000)
//          println("\nsimTime : " + simTime())
//        }
//      }
      fork{
//        disableSimWave()
//        sleep(600e9.toLong)
//        enableSimWave()
//        sleep(systemClkPeriod*1000000)
//        simFailure()

        while(true){
          disableSimWave()
          sleep(systemClkPeriod*500000)
          enableSimWave()
          sleep(systemClkPeriod*100)
        }
      }




      val tcpJtag = JtagTcp(
        jtag = dut.cpu.jtag,
        jtagClkPeriod = jtagClkPeriod
      )

      val uartTx = UartDecoder(
        uartPin =  dut.uartA.uart.txd,
        baudPeriod = uartBaudPeriod
      )

      val uartRx = UartEncoder(
        uartPin = dut.uartA.uart.rxd,
        baudPeriod = uartBaudPeriod
      )

      val sdram = SdramModel(
        io = dut.sdramA.sdram,
        layout = dut.sdramA.logic.layout,
        clockDomain = clockDomain
      )
//      sdram.loadBin(0, "software/standalone/dhrystone/build/dhrystone.bin")

      val linuxPath = "../buildroot/output/images/"
      sdram.loadBin(0x00000000, "software/standalone/machineModeSbi/build/machineModeSbi.bin")
      sdram.loadBin(0x00400000, linuxPath + "Image")
      sdram.loadBin(0x00BF0000, linuxPath + "dtb")
      sdram.loadBin(0x00C00000, linuxPath + "rootfs.cpio")
    }
  }
}