.macro print_str %str
	.data
	print_str_message: .asciiz %str
	.text
	push a0
	push v0
	la a0, print_str_message
	li v0, 4
	syscall
	pop v0
	pop a0
.end_macro

.include "display_constants_enh.asm"
.include "display_enh.asm"

.data
.text

.global main
main:
	# turn on enhanced mode, FB only, 15ms/frame
	li  t0, 15
	sll t0, t0, DISPLAY_MODE_MS_SHIFT
	or  t0, t0, DISPLAY_MODE_ENHANCED
	or  t0, t0, DISPLAY_MODE_FB_ENABLE
	or  t0, t0, DISPLAY_MODE_TM_ENABLE
	sw  t0, DISPLAY_CTRL

	# put the framebuffer in front of the tilemap
	#li t0, 1
	#sw t0, DISPLAY_ORDER

	# reset everything
	sw zero, DISPLAY_RESET

	#j test_tilemap
	#j test_default_palette
	j test_mouse_follower
	j test_fb_palette_offset
	j test_mouse
	j test_kb

	li v0, 10
	syscall

# -------------------------------------------------------------------------------------------------

test_default_palette:

	# draw an extremely lazy palette view
	li s2, 0
	li s1, 0
	_yloop:
		li s0, 0
		_xloop:
			mul a0, s0, 2
			add a0, a0, 10
			mul a1, s1, 2
			add a1, a1, 10
			move a2, s2
			jal display_set_pixel
			mul a0, s0, 2
			add a0, a0, 11
			mul a1, s1, 2
			add a1, a1, 10
			move a2, s2
			jal display_set_pixel
			mul a0, s0, 2
			add a0, a0, 10
			mul a1, s1, 2
			add a1, a1, 11
			move a2, s2
			jal display_set_pixel
			mul a0, s0, 2
			add a0, a0, 11
			mul a1, s1, 2
			add a1, a1, 11
			move a2, s2
			jal display_set_pixel
			add s2, s2, 1
		add s0, s0, 1
		blt s0, 16, _xloop
	add s1, s1, 1
	blt s1, 16, _yloop

	# set bg color to non-black so we can see if the transparency is doing the Thing
	li t0, 0x332211
	sw t0, DISPLAY_PALETTE_RAM

	# s0 = fb palette offset
	li s0, 0

	_loop:
		lw t1, DISPLAY_MOUSE_WHEEL_Y
		beq t1, 0, _endif
			add s0, s0, t1
			and s0, s0, 0xFF
			sw  s0, DISPLAY_FB_PAL_OFFS
			move a0, s0
			li v0, 1
			syscall
			print_str "\n"
		_endif:

		sw zero, DISPLAY_SYNC
		lw zero, DISPLAY_SYNC
	j _loop

# -------------------------------------------------------------------------------------------------

.data
	waterfall_palette: .word
		0x002033
		0x002044
		0x002055
		0x002077
		0x002099
		0x0020BB
		0x0020EE
.text

test_fb_palette_offset:
	# fill framebuffer with horizontal stripes of 1, 2, 3, 4, 5, 6, 7, repeat
	li t0, DISPLAY_FB_RAM

	li s2, 0x01010101
	li s1, 0
	_yloop:
		li s0, 0
		_xloop:
			sw s2, (t0)
			add t0, t0, 4
		add s0, s0, 4
		blt s0, DISPLAY_W, _xloop

		add s2, s2, 0x01010101
		bne s2, 0x08080808, _endif
			li s2, 0x01010101
		_endif:
	add s1, s1, 1
	blt s1, DISPLAY_H, _yloop

	# load palette twice, sequentially, so when we change the palette index,
	# it "wraps around"
	la a0, waterfall_palette
	li a1, 1
	li a2, 7
	jal display_load_palette

	la a0, waterfall_palette
	li a1, 8
	li a2, 7
	jal display_load_palette

	# s0 is the palette offset
	li s0, 6

	_loop:
		# cycle palette offset
		sub s0, s0, 1
		bne s0, -1, _skip
			li s0, 6
		_skip:

		sw s0, DISPLAY_FB_PAL_OFFS

		# flip
		sw zero, DISPLAY_SYNC
		# sync
		lw zero, DISPLAY_SYNC
	j _loop

# -------------------------------------------------------------------------------------------------

test_mouse:
	li s0, 0

	_loop:
		lw t0, DISPLAY_MOUSE_X
		blt t0, 0, _endif
			lw t1, DISPLAY_MOUSE_WHEEL_Y
			beq t1, 0, _endif_wheel
				add s0, s0, t1
				and s0, s0, 0xFF
				sw  s0, DISPLAY_FB_PAL_OFFS
				move a0, s0
				li v0, 1
				syscall
				print_str "\n"
			_endif_wheel:

			lw t1, DISPLAY_MOUSE_Y
			lw t2, DISPLAY_MOUSE_HELD
			and t2, t2, MOUSE_LBUTTON
			beq t2, 0, _endif
				mul t1, t1, DISPLAY_W
				add t0, t0, t1
				add t0, t0, DISPLAY_FB_RAM

				li t1, COLOR_BLUE
				sb t1, (t0)
		_endif:

		# flip
		sw zero, DISPLAY_SYNC
		# sync
		lw zero, DISPLAY_SYNC
	j _loop

# -------------------------------------------------------------------------------------------------

.data
	dot_x: .word 63
	dot_y: .word 63
.text

test_kb:
	_loop:
		li a0, KEY_UP
		jal display_is_key_held
		beq v0, 0, _endif_u
			lw t0, dot_y
			beq t0, 0, _endif_u
				sub t0, t0, 1
				sw t0, dot_y
		_endif_u:

		li a0, KEY_DOWN
		jal display_is_key_held
		beq v0, 0, _endif_d
			lw t0, dot_y
			beq t0, 127, _endif_d
				add t0, t0, 1
				sw t0, dot_y
		_endif_d:

		li a0, KEY_LEFT
		jal display_is_key_held
		beq v0, 0, _endif_l
			lw t0, dot_x
			beq t0, 0, _endif_l
				sub t0, t0, 1
				sw t0, dot_x
		_endif_l:

		li a0, KEY_RIGHT
		jal display_is_key_held
		beq v0, 0, _endif_r
			lw t0, dot_x
			beq t0, 127, _endif_r
				add t0, t0, 1
				sw t0, dot_x
		_endif_r:

		sw zero, DISPLAY_FB_CLEAR

		lw  t0, dot_x
		lw  t1, dot_y
		mul t1, t1, DISPLAY_W
		add t0, t0, t1
		add t0, t0, DISPLAY_FB_RAM

		li t1, COLOR_WHITE
		sb t1, (t0)

		sw zero, DISPLAY_SYNC
		lw zero, DISPLAY_SYNC
	j _loop

# -------------------------------------------------------------------------------------------------

.data
	last_mouse_x: .word 0x3F00
	last_mouse_y: .word 0x3F00
	follower_x:   .word 0x3F00
	follower_y:   .word 0x3F00
	follower_vx:  .word 0
	follower_vy:  .word 0

	.eqv NUM_FOLLOWER_GFX_TILES 1

	.align 2
	follower_gfx: .byte
		# lowercase b (obvious when flipped horizontally or vertically)
		 0  0  0  0  0  0  0  0
		 0 63 63  0  0  0  0  0
		 0 63 63  0  0  0  0  0
		 0 63 63 63 63  0  0  0
		 0 63 63  0  0 63 63  0
		 0 63 63  0  0 63 63  0
		 0 63 63  0  0 63 63  0
		 0 63 63 63 63  0  0  0
.text

.eqv VELOCITY_DECAY 0x00F0 # 0.93

test_mouse_follower:
	# load the graphics
	la a0, follower_gfx
	li a1, 0
	li a2, NUM_FOLLOWER_GFX_TILES
	jal display_load_sprite_gfx

	# set up the sprite
	li t1, DISPLAY_SPR_TABLE

	lw  t0, follower_x
	srl t0, t0, 8
	sb  t0, 0(t1)

	lw  t0, follower_y
	srl t0, t0, 8
	sb  t0, 1(t1)

	sb  zero, 2(t1) # tile index

	li  t0, BIT_ENABLE
	sb  t0, 3(t1) # flags

	_loop:
		# update last mouse position
		lw t0, DISPLAY_MOUSE_X
		beq t0, -1, _mouse_offscreen
			lw t1, DISPLAY_MOUSE_Y
			sll t0, t0, 8
			sll t1, t1, 8
			sw  t0, last_mouse_x
			sw  t1, last_mouse_y
		_mouse_offscreen:

		# (t0, t1) is mouse position in 24.8
		lw t0, last_mouse_x
		lw t1, last_mouse_y

		# calculate vector to mouse position in (t2, t3)
		lw  a0, follower_x
		sub a0, t0, a0

		lw  a1, follower_y
		sub a1, t1, a1

		# normalize vector
		jal normalize_24_8

		# scale it down
		sra v0, v0, 1
		sra v1, v1, 1

		# add to follower velocity (and decay velocity)
		lw  t0, follower_vx
		add t0, t0, v0
		mul t0, t0, VELOCITY_DECAY
		sra t0, t0, 8
		sw  t0, follower_vx
		lw  t1, follower_vy
		mul t1, t1, VELOCITY_DECAY
		sra t1, t1, 8
		add t1, t1, v1
		sw  t1, follower_vy

		# add velocity to position
		lw  t2, follower_x
		add t2, t2, t0
		sw  t2, follower_x

		lw  t3, follower_y
		add t3, t3, t1
		sw  t3, follower_y

		# position sprite at follower position (minus 4 on each axis, to center it)
		li  t9, DISPLAY_SPR_TABLE

		lw  t0, follower_x
		srl t0, t0, 8
		sub t0, t0, 4
		sb  t0, 0(t9)

		lw  t0, follower_y
		srl t0, t0, 8
		sub t0, t0, 4
		sb  t0, 1(t9)

		# calculate flip flags based on follower's position relative to mouse
		li  t8, BIT_ENABLE # t8 = flags

		lw  t0, follower_x
		lw  t1, last_mouse_x
		blt t0, t1, _no_flip_x
			or t8, t8, BIT_HFLIP
		_no_flip_x:

		lw  t0, follower_y
		lw  t1, last_mouse_y
		blt t0, t1, _no_flip_y
			or t8, t8, BIT_VFLIP
		_no_flip_y:

		sb t8, 3(t9)

		sw zero, DISPLAY_SYNC
		lw zero, DISPLAY_SYNC
	j _loop

# ------------------------
normalize_24_8:
enter s0, s1
move s0, a0
move s1, a1
	jal hypot_24_8
	beq v0, 0, _endif
		sll s1, s1, 8
		div v1, s1, v0
		sll s0, s0, 8
		div v0, s0, v0
	_endif:
leave s0, s1

# ------------------------

hypot_24_8:
enter
	# square dx and dy; leave in 16.16
	mul a0, a0, a0
	mul a1, a1, a1

	# sqrt(dx^2 + dy^2)
	add a0, a0, a1
	jal sqrt_16_16

	# 16.16 -> 24.8
	sra v0, v0, 8
leave

# ------------------------

sqrt_16_16:
	# t0 = 0x40000000
	li t0, 0x40000000

	# v0 = 0
	li v0, 0

	# while( t0 > 0x40 )
	_sqrt_16_16_loop:
	ble t0, 0x40, _sqrt_16_16_break
		# t1 = v0 + t0
		addu t1, v0, t0

		# if( a0 >= t1 )
		blt a0, t1, _sqrt_16_16_less
			# a0 -= t1
			subu a0, a0, t1
			# v0 = t1 + t0 // equivalent to v0 += 2*t0
			addu v0, t1, t0
		_sqrt_16_16_less:

		# a0 <<= 1
		sll a0, a0, 1
		# t0 >>= 1
		srl t0, t0, 1
	j _sqrt_16_16_loop

_sqrt_16_16_break:
	# v0 >>= 8
	srl v0, v0, 8
	jr ra

# -------------------------------------------------------------------------------------------------

.data
	.eqv NUM_TEST_TILES 2
	.align 2
	test_tile_gfx: .byte
		# using the RGB222 palette indexes
		0b110000 0b110000 0b110000 0b110000 0b110000 0b110000 0b110000 0b110000
		0b111000 0b111000 0b111000 0b111000 0b111000 0b111000 0b111000 0b111000
		0b111100 0b111100 0b111100 0b111100 0b111100 0b111100 0b111100 0b111100
		0b001100 0b001100 0b001100 0b001100 0b001100 0b001100 0b001100 0b001100
		0b001111 0b001111 0b001111 0b001111 0b001111 0b001111 0b001111 0b001111
		0b000011 0b000011 0b000011 0b000011 0b000011 0b000011 0b000011 0b000011
		0b000010 0b000010 0b000010 0b000010 0b000010 0b000010 0b000010 0b000010
		0b100010 0b100010 0b100010 0b100010 0b100010 0b100010 0b100010 0b100010

		# lowercase b (obvious when flipped horizontally or vertically)
		 0  0  0  0  0  0  0  0
		 0 63 63  0  0  0  0  0
		 0 63 63  0  0  0  0  0
		 0 63 63 63 63  0  0  0
		 0 63 63  0  0 63 63  0
		 0 63 63  0  0 63 63  0
		 0 63 63  0  0 63 63  0
		 0 63 63 63 63  0  0  0
.text

test_tilemap:
	print_str "left/middle/right mouse buttons draw tiles.\n"
	print_str "shift+LMB/RMB flip individual tiles.\n"
	print_str "mouse wheel changes tilemap palette offset.\n"
	print_str "arrows scroll tilemap.\n"
	print_str "shift+up/right flips all tiles in tilemap.\n"

	# load graphics
	la a0, test_tile_gfx
	li a1, 1
	li a2, NUM_TEST_TILES
	jal display_load_tilemap_gfx

	# put A Tile at (5, 2)
	li a0, 5
	li a1, 2
	li a2, 1
	li a3, 0
	jal display_set_tile

	# set bg color to non-black so we can see if the transparency is doing the Thing
	li t0, 0x332211
	sw t0, DISPLAY_PALETTE_RAM

	# tilemap palette offset
	li s0, 0

	_loop:
		# drawing tiles
		jal test_tilemap_mouse_input

		# flipping tiles
		jal test_tilemap_key_input

		# display and sync
		sw zero, DISPLAY_SYNC
		lw zero, DISPLAY_SYNC
	j _loop

test_tilemap_mouse_to_tile_addr:
	lw  t1, DISPLAY_MOUSE_X
	lw  t0, DISPLAY_TM_SCX
	add t1, t1, t0
	and t1, t1, 0xFF

	lw  t2, DISPLAY_MOUSE_Y
	lw  t0, DISPLAY_TM_SCY
	add t2, t2, t0
	and t2, t2, 0xFF

	div t1, t1, TILE_W
	div t2, t2, TILE_H

	mul t2, t2, TM_ROW_BYTE_SIZE
	mul t1, t1, 2
	add t2, t2, t1
	add v0, t2, DISPLAY_TM_TABLE
jr ra

test_tilemap_mouse_to_tile_coords:
	lw  v0, DISPLAY_MOUSE_X
	lw  t0, DISPLAY_TM_SCX
	add v0, v0, t0
	and v0, v0, 0xFF

	lw  v1, DISPLAY_MOUSE_Y
	lw  t0, DISPLAY_TM_SCY
	add v1, v1, t0
	and v1, v1, 0xFF

	div v0, v0, TILE_W
	div v1, v1, TILE_H
jr ra

test_tilemap_mouse_input:
enter
	# if holding shift...
	li  a0, KEY_SHIFT
	jal display_is_key_held
	beq v0, 0, _no_shift
		# then clicking will flip tiles.
		lw  t0, DISPLAY_MOUSE_RELEASED
		and t0, t0, MOUSE_LBUTTON
		beq t0, 0, _endif_shift_l
			lw  t0, DISPLAY_MOUSE_X
			blt t0, 0, _endif_shift_l

			jal test_tilemap_mouse_to_tile_addr

			lb  t0, 1(v0)
			xor t0, t0, BIT_HFLIP
			sb  t0, 1(v0)
		_endif_shift_l:

		lw  t0, DISPLAY_MOUSE_RELEASED
		and t0, t0, MOUSE_RBUTTON
		beq t0, 0, _endif_shift_r
			lw  t0, DISPLAY_MOUSE_X
			blt t0, 0, _endif_shift_r

			jal test_tilemap_mouse_to_tile_addr

			lb  t0, 1(v0)
			xor t0, t0, BIT_VFLIP
			sb  t0, 1(v0)
		_endif_shift_r:

		# scroll wheel changes palette offset.
		# weirdly, we have to use DISPLAY_MOUSE_WHEEL_X here because of the way
		# scroll wheel events are delivered to Java - holding shift and scrolling
		# counts as a *horizontal* scroll. Weird.
		lw t0, DISPLAY_MOUSE_WHEEL_X
		beq t0, 0, _endif_shift_wheel
			lw  t0, DISPLAY_MOUSE_X
			blt t0, 0, _endif_shift_wheel

			jal test_tilemap_mouse_to_tile_addr

			lb  t0, 1(v0)
			lw  t1, DISPLAY_MOUSE_WHEEL_X
			sll t1, t1, 4
			add t0, t0, t1
			and t0, t0, 0xFF
			sb  t0, 1(v0)
		_endif_shift_wheel:

	j _endif_outer
	_no_shift:
		lw  t0, DISPLAY_MOUSE_HELD
		and t0, t0, MOUSE_LBUTTON
		beq t0, 0, _endif_l
			lw  t0, DISPLAY_MOUSE_X
			blt t0, 0, _endif_l

			jal test_tilemap_mouse_to_tile_coords

			move a0, v0
			move a1, v1
			li a2, 1
			li a3, 0
			jal display_set_tile
		_endif_l:

		lw  t0, DISPLAY_MOUSE_HELD
		and t0, t0, MOUSE_MBUTTON
		beq t0, 0, _endif_m
			lw  t0, DISPLAY_MOUSE_X
			blt t0, 0, _endif_m

			jal test_tilemap_mouse_to_tile_coords

			move a0, v0
			move a1, v1
			li a2, 2
			li a3, 0
			jal display_set_tile
		_endif_m:

		lw  t0, DISPLAY_MOUSE_HELD
		and t0, t0, MOUSE_RBUTTON
		beq t0, 0, _endif_r
			lw  t0, DISPLAY_MOUSE_X
			blt t0, 0, _endif_r

			jal test_tilemap_mouse_to_tile_coords

			move a0, v0
			move a1, v1
			li a2, 0
			li a3, 0
			jal display_set_tile
		_endif_r:

		# scroll wheel pans around
		lw t0, DISPLAY_MOUSE_WHEEL_X
		beq t0, 0, _endif_wheel_x
			lw  t1, DISPLAY_TM_SCX
			add t1, t1, t0
			and t1, t1, 0xFF
			sw  t1, DISPLAY_TM_SCX
		_endif_wheel_x:

		lw t0, DISPLAY_MOUSE_WHEEL_Y
		beq t0, 0, _endif_wheel_y
			lw  t1, DISPLAY_TM_SCY
			add t1, t1, t0
			and t1, t1, 0xFF
			sw  t1, DISPLAY_TM_SCY
		_endif_wheel_y:
	_endif_outer:
leave

test_tilemap_key_input:
enter
	li  a0, KEY_SHIFT
	jal display_is_key_held
	beq v0, 0, _no_shift
		li  a0, KEY_UP
		jal display_is_key_pressed
		bne v0, 1, _endif_shift_u
			# toggle vflip of all tiles
			li a0, BIT_VFLIP
			jal test_tilemap_flippem
		_endif_shift_u:

		li  a0, KEY_RIGHT
		jal display_is_key_pressed
		bne v0, 1, _endif_shift_r
			# toggle hflip of all tiles
			li a0, BIT_HFLIP
			jal test_tilemap_flippem
		_endif_shift_r:
	j _endif_outer
	_no_shift:
		li  a0, KEY_UP
		jal display_is_key_held
		beq v0, 0, _endif_u
			lw  t0, DISPLAY_TM_SCY
			sub t0, t0, 1
			and t0, t0, 0xFF
			sw  t0, DISPLAY_TM_SCY
		_endif_u:

		li  a0, KEY_DOWN
		jal display_is_key_held
		beq v0, 0, _endif_d
			lw  t0, DISPLAY_TM_SCY
			add t0, t0, 1
			and t0, t0, 0xFF
			sw  t0, DISPLAY_TM_SCY
		_endif_d:

		li  a0, KEY_LEFT
		jal display_is_key_held
		beq v0, 0, _endif_l
			lw  t0, DISPLAY_TM_SCX
			sub t0, t0, 1
			and t0, t0, 0xFF
			sw  t0, DISPLAY_TM_SCX
		_endif_l:

		li  a0, KEY_RIGHT
		jal display_is_key_held
		beq v0, 0, _endif_r
			lw  t0, DISPLAY_TM_SCX
			add t0, t0, 1
			and t0, t0, 0xFF
			sw  t0, DISPLAY_TM_SCX
		_endif_r:
	_endif_outer:
leave

# a0 = bitmask that will be XORed with tile flags
test_tilemap_flippem:
enter
	li t8, DISPLAY_TM_TABLE # t8 = pointer
	_loop:
		lb  t0, 1(t8)
		xor t0, t0, a0
		sb  t0, 1(t8)
	add t8, t8, 2
	blt t8, DISPLAY_SPR_TABLE, _loop
leave