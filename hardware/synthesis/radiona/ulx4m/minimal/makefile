VERILOG = ../../../../netlist/Ulx4mMinimal.v

compile : bin/toplevel.bit

prog: bin/toplevel.bit
	fujprog $<

bin/toplevel.json: ${VERILOG}
	mkdir -p bin
	rm -f Ulx4mMinimal.v*.bin
	cp ../../../../netlist/Ulx4mMinimal.v*.bin . | true
	yosys \
		-p "synth_ecp5 -top Ulx4mMinimal -json $@" \
		$<

bin/toplevel.config: bin/toplevel.json
	nextpnr-ecp5 \
		--json $< \
		--textcfg $@ \
		--lpf ulx4m_v20_led.lpf \
		--um-45k \
		--package CABGA381

bin/toplevel.bit: bin/toplevel.config
	ecppack $< $@

clean:
	$(RM) -rf bin *.bin
