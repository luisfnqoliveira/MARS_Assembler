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
	sw  t0, DISPLAY_CTRL

	# put the framebuffer in front of the tilemap
	#li t0, 1
	#sw t0, DISPLAY_ORDER

	# reset everything
	sw zero, DISPLAY_RESET

	j test_mouse_follower
	j test_fb_palette_offset
	j test_mouse
	j test_kb

	li v0, 10
	syscall

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

	_loop:
		lw t0, DISPLAY_MOUSE_X
		blt t0, 0, _endif
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
	follower_x: 0x3F00
	follower_y: 0x3F00
.text

test_mouse_follower:

	_loop:
		lw t0, DISPLAY_MOUSE_X
		beq t0, -1, _mouse_offscreen
			lw t1, DISPLAY_MOUSE_Y
			sll t0, t0, 8
			sll t1, t1, 8

			# (t0, t1) is mouse position in 24.8
			# calculate vector to mouse position in (t2, t3)
			lw  t2, follower_x
			sub t2, t0, t2

			lw  t3, follower_y
			sub t3, t1, t3

			# scale vector by 1/4
			sra t2, t2, 4
			sra t3, t3, 4

			# add to follower position
			lw  t0, follower_x
			add t0, t0, t2
			sw  t0, follower_x

			lw  t0, follower_y
			add t0, t0, t3
			sw  t0, follower_y
		_mouse_offscreen:

		# clear display
		sw zero, DISPLAY_FB_CLEAR

		# draw dot at follower position
		lw  t0, follower_y
		srl t0, t0, 8
		mul t0, t0, DISPLAY_W
		lw  t1, follower_x
		srl t1, t1, 8
		add t0, t0, t1
		add t0, t0, DISPLAY_FB_RAM
		li  t1, COLOR_WHITE
		sb  t1, (t0)

		sw zero, DISPLAY_SYNC
		lw zero, DISPLAY_SYNC
	j _loop