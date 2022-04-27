VERILOG = ../../../../netlist/Ulx4mSmp.v pll_linux.v pll_linux2.v

DEVICE ?= um-45k

SDRAM_SIZE ?= 32

compile : bin/toplevel.bit

prog: bin/toplevel.bit
	ujprog $<

bin/toplevel.json: ${VERILOG}
	mkdir -p bin
	rm -f Ulx4mSmp.v*.bin
	cp ../../../../netlist/Ulx4mSmp.v*.bin . | true
	yosys \
		-p "synth_ecp5 -top Ulx4mSmp -json $@" \
		${VERILOG}

#  -noflatten -abc9

bin/toplevel.config: bin/toplevel.json
	nextpnr-ecp5 \
		--json $< \
		--textcfg $@ \
		--lpf ulx4m_v20_linux_uboot.lpf \
		--timing-allow-fail \
		--${DEVICE} \
		--freq 50 \
		--package CABGA381 ${NEXTPNR_FLAGS}

bin/toplevel.bit: bin/toplevel.config
	ecppack --compress $< $@

pll:
	ecppll \
                -i 25 \
                --clkout0 125 \
		--clkout1 100 \
		--clkout2 50 \
		--clkout3 25 \
		--phase1 270 \
                -n  pll_linux\
                -f pll_linux.v

pll2:
	ecppll \
                -i 52.0833 \
                --clkout0 48 \
                -n pll_linux2\
                -f pll_linux2.v
clean:
	$(RM) -rf bin *.bin
