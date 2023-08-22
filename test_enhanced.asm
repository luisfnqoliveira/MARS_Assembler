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

.data
.text

.global main
main:
	# turn on enhanced mode, FB only, 16ms/frame
	li  t0, 16
	sll t0, t0, DISPLAY_MODE_MS_SHIFT
	or  t0, t0, DISPLAY_MODE_ENHANCED
	or  t0, t0, DISPLAY_MODE_FB_ENABLE
	sw  t0, DISPLAY_CTRL

	# put the framebuffer in front of the tilemap
	#li t0, 1
	#sw t0, DISPLAY_ORDER

	# reset everything
	sw zero, DISPLAY_RESET

	#j test_fb_palette_offset
	j test_mouse

	li v0, 10
	syscall

# -------------------------------------------------------------------------------------------------

test_fb_palette_offset:
	# put some bullshit in the framebuffer
	li t0, DISPLAY_FB_RAM
	li s0, 0
	_fbloop:
		sb s0, (t0)
		add t0, t0, 1
	add s0, s0, 1
	blt s0, 1024, _fbloop

	li s0, 0

	_loop:
		# cycle palette offset
		add s0, s0, 1
		blt s0, 256, _skip
			li s0, 0
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