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

	# reset palette
	sw zero, DISPLAY_PALETTE_RESET

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

	li v0, 10
	syscall


