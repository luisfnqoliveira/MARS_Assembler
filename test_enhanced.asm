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

.eqv DISPLAY_W        128
.eqv DISPLAY_H        128
.eqv DISPLAY_W_SHIFT    7

.eqv DISPLAY_MODE_MS_SHIFT  16
.eqv DISPLAY_MODE_ENHANCED  0x100
.eqv DISPLAY_MODE_FB_ENABLE 1
.eqv DISPLAY_MODE_TM_ENABLE 2

.eqv DISPLAY_CTRL           0xFFFF0000
.eqv DISPLAY_ORDER          0xFFFF0004
.eqv DISPLAY_SYNC           0xFFFF0008
.eqv DISPLAY_FB_CLEAR       0xFFFF000C
.eqv DISPLAY_PALETTE_RESET  0xFFFF0010
.eqv DISPLAY_FB_PAL_OFFS    0xFFFF0014

.eqv DISPLAY_TM_SCX         0xFFFF0020
.eqv DISPLAY_TM_SCY         0xFFFF0024
.eqv DISPLAY_TM_PAL_OFFS    0xFFFF0028

.eqv DISPLAY_KEY_HELD       0xFFFF0040
.eqv DISPLAY_KEY_PRESSED    0xFFFF0044
.eqv DISPLAY_KEY_RELEASED   0xFFFF0048
.eqv DISPLAY_MOUSE_X        0xFFFF004C
.eqv DISPLAY_MOUSE_Y        0xFFFF0050
.eqv DISPLAY_MOUSE_HELD     0xFFFF0054
.eqv DISPLAY_MOUSE_PRESSED  0xFFFF0058
.eqv DISPLAY_MOUSE_RELEASED 0xFFFF005C

.eqv DISPLAY_PALETTE_RAM    0xFFFF0C00
.eqv DISPLAY_FB_RAM         0xFFFF1000

.eqv DISPLAY_TM_TABLE       0xFFFF5000
.eqv DISPLAY_SPR_TABLE      0xFFFF5800
.eqv DISPLAY_TM_GFX         0xFFFF6000
.eqv DISPLAY_SPR_GFX        0xFFFFA000

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


