
.include "macros.asm"

# -------------------------------------------------------------------------------------------------

# a0 = milliseconds per frame
# a1 = enable framebuffer
# a2 = enable tilemap
display_init:
	sll a0, a0, DISPLAY_MODE_MS_SHIFT
	beq a1, 0, _no_fb
		or a0, a0, DISPLAY_MODE_FB_ENABLE
	_no_fb:

	beq a2, 0, _no_tm
		or a0, a0, DISPLAY_MODE_TM_ENABLE
	_no_tm:
	or  a0, a0, DISPLAY_MODE_ENHANCED
	sw  a0, DISPLAY_CTRL

	# reset everything!
	sw zero, DISPLAY_RESET
jr ra

# -------------------------------------------------------------------------------------------------

# a0 = key to check
# returns 1 if held, 0 if not
display_is_key_held:
	sw a0, DISPLAY_KEY_HELD
	lw v0, DISPLAY_KEY_HELD
jr ra

# -------------------------------------------------------------------------------------------------

# a0 = key to check
# returns 1 if pressed on this frame, 0 if not
display_is_key_pressed:
	sw a0, DISPLAY_KEY_PRESSED
	lw v0, DISPLAY_KEY_PRESSED
jr ra

# -------------------------------------------------------------------------------------------------

# a0 = key to check
# returns 1 if released on this frame, 0 if not
display_is_key_released:
	sw a0, DISPLAY_KEY_RELEASED
	lw v0, DISPLAY_KEY_RELEASED
jr ra

# -------------------------------------------------------------------------------------------------

# Loads palette entries into palette RAM. Each palette entry is a word in the format
# 0xRRGGBB, e.g. 0xFF0000 is pure red, 0x00FF00 is pure green, etc.
# a0 = address of palette array to load (use la for this argument)
# a1 = start color index to load it into. don't forget, index 0 is the background color!
# a2 = number of colors. shouldn't be < 1 or > 256, or else weird shit happens
display_load_palette:
	mul a1, a1, 4
	add a1, a1, DISPLAY_PALETTE_RAM

	_loop:
		lw t0, (a0)
		sw t0, (a1)
		add a0, a0, 4
		add a1, a1, 4
	sub a2, a2, 1
	bgt a2, 0, _loop
jr ra

# -------------------------------------------------------------------------------------------------

# sets 1 pixel to a given color.
# (0, 0) is in the top LEFT, and Y increases DOWNWARDS!
# arguments:
#	a0 = x
#	a1 = y
#	a2 = color
display_set_pixel:
	blt a0, 0, _return
	bge a0, DISPLAY_W, _return
	blt a1, 0, _return
	bge a1, DISPLAY_H, _return

	sll t0, a1, DISPLAY_W_SHIFT
	add t0, t0, a0
	add t0, t0, DISPLAY_FB_RAM
	sb  a2, (t0)
_return:
	jr  ra

# -------------------------------------------------------------------------------------------------

# a0 = tile x
# a1 = tile y
# a2 = tile index
# a3 = tile flags
display_set_tile:
	mul a1, a1, TM_ROW_BYTE_SIZE
	mul a0, a0, 2
	add a1, a1, a0
	add a1, a1, DISPLAY_TM_TABLE
	sb a2, (a1)
	sb a3, 1(a1)
	jr ra

# -------------------------------------------------------------------------------------------------

# a0 = address of tile graphics to load
# a1 = start tile index to load it into
# a2 = number of *tiles* to load
display_load_tilemap_gfx:
	mul a1, a1, BYTES_PER_TILE
	add a1, a1, DISPLAY_TM_GFX
	mul a2, a2, BYTES_PER_TILE
	j PRIVATE_tilecpy

# -------------------------------------------------------------------------------------------------

# a0 = address of tile graphics to load
# a1 = start tile index to load it into
# a2 = number of *tiles* to load
display_load_sprite_gfx:
	mul a1, a1, BYTES_PER_TILE
	add a1, a1, DISPLAY_SPR_GFX
	mul a2, a2, BYTES_PER_TILE
	j PRIVATE_tilecpy

# -------------------------------------------------------------------------------------------------

# like memcpy, but (src, dest, bytes) instead of (dest, src, bytes).
# also assumes number of tiles is a nonzero multiple of 4
# a0 = source
# a1 = target
# a2 = number of bytes
PRIVATE_tilecpy:
	_loop:
		lw t0, (a0)
		sw t0, (a1)
		add a0, a0, 4
		add a1, a1, 4
		sub a2, a2, 4
	bgt a2, 0, _loop
	jr ra