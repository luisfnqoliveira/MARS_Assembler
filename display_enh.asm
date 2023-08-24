# driver philosophy: if it's more than a single load or store, or
# if it isn't blindingly obvious what it does (e.g. "magical stores"),
# make it a driver function. otherwise, it's unnecessary.

# e.g. loading from DISPLAY_MOUSE_HELD or changing DISPLAY_TM_SCX are
# both so simple and obvious that they don't need a driver function.
# but frame sync is "sw zero, DISPLAY_SYNC" which is just baffling.

.include "macros.asm"

# -------------------------------------------------------------------------------------------------
# Display control and frame sync
# -------------------------------------------------------------------------------------------------

# void display_init(int msPerFrame, bool enableFB, bool enableTM)
#   Initialize the display, putting it into enhanced mode, and resetting everything.
#   This should be the first thing you call after any non-display-related setup!
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

	# reset everything! you might think we should do this *first*, but we
	# don't actually know if the display is in enhanced mode before the
	# above store.
	sw zero, DISPLAY_RESET
jr ra

# -------------------------------------------------------------------------------------------------

# void display_enable_fb()
#   enable the framebuffer if it isn't already.
display_enable_fb:
	lw t0, DISPLAY_CTRL
	or t0, t0, DISPLAY_MODE_FB_ENABLE
	sw t0, DISPLAY_CTRL
jr ra

# -------------------------------------------------------------------------------------------------

# void display_disable_fb()
#   disable the framebuffer if it's enabled.
#   if the tilemap is not enabled, this has no effect. (at least one has to be enabled.)
display_disable_fb:
	lw  t0, DISPLAY_CTRL
	and t1, t0, DISPLAY_MODE_TM_ENABLE
	beq t1, 0, _return

	# god I wish MARS could do constant expressions.
	li  t1, DISPLAY_MODE_FB_ENABLE
	not t1, t1
	and t0, t0, t1
	sw  t0, DISPLAY_CTRL
_return:
jr ra

# -------------------------------------------------------------------------------------------------

# void display_enable_tm()
#   enable the tilemap if it isn't already.
display_enable_tm:
	lw t0, DISPLAY_CTRL
	or t0, t0, DISPLAY_MODE_TM_ENABLE
	sw t0, DISPLAY_CTRL
jr ra

# -------------------------------------------------------------------------------------------------

# void display_disable_tm()
#   disable the tilemap if it's enabled.
#   if the framebuffer is not enabled, this has no effect. (at least one has to be enabled.)
display_disable_tm:
	lw  t0, DISPLAY_CTRL
	and t1, t0, DISPLAY_MODE_FB_ENABLE
	beq t1, 0, _return

	# god I wish MARS could do constant expressions.
	li  t1, DISPLAY_MODE_TM_ENABLE
	not t1, t1
	and t0, t0, t1
	sw  t0, DISPLAY_CTRL
_return:
jr ra

# -------------------------------------------------------------------------------------------------

# void display_finish_frame()
#   call this at the end of each frame to display the graphics, update
#   input, and wait the appropriate amount of time until the next frame.
display_finish_frame:
	sw zero, DISPLAY_SYNC
	lw zero, DISPLAY_SYNC
jr ra

# -------------------------------------------------------------------------------------------------
# Input
# -------------------------------------------------------------------------------------------------

# sets %reg to 1 if %key is being held, 0 if not
.macro display_is_key_held %reg, %key
	li %reg, %key
	sw %reg, DISPLAY_KEY_HELD
	lw %reg, DISPLAY_KEY_HELD
.end_macro

# -------------------------------------------------------------------------------------------------

# sets %reg to 1 if %key was pressed on this frame, 0 if not
.macro display_is_key_pressed %reg, %key
	li %reg, %key
	sw %reg, DISPLAY_KEY_PRESSED
	lw %reg, DISPLAY_KEY_PRESSED
.end_macro

# -------------------------------------------------------------------------------------------------

# sets %reg to 1 if %key was released on this frame, 0 if not
.macro display_is_key_released %reg, %key
	li %reg, %key
	sw %reg, DISPLAY_KEY_RELEASED
	lw %reg, DISPLAY_KEY_RELEASED
.end_macro

# -------------------------------------------------------------------------------------------------
# Palette
# -------------------------------------------------------------------------------------------------

# void display_load_palette(int* palette, int startIndex, int numColors)
#   Loads palette entries into palette RAM. Each palette entry is a word in the format
#   0xRRGGBB, e.g. 0xFF0000 is pure red, 0x00FF00 is pure green, etc.
#   a0 is the address of palette array to load (use la for this argument).
#   a1 is the first color index to load it into. don't forget, index 0 is the background color!
#   a2 is the number of colors to load. shouldn't be < 1 or > 256, or else weird shit happens
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
# Framebuffer
# -------------------------------------------------------------------------------------------------

# void display_set_pixel(int x, int y, int color)
#   sets 1 pixel to a given color. valid colors are in the range [0, 255].
#   (0, 0) is in the top LEFT, and Y increases DOWNWARDS!
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
# Tilemap
# -------------------------------------------------------------------------------------------------

# void display_set_tile(int tx, int ty, int tileIndex, int flags)
#   sets the tile at *tile* coordinates (tx, ty) to the given tile index and flags.
display_set_tile:
	mul a1, a1, TM_ROW_BYTE_SIZE
	mul a0, a0, 2
	add a1, a1, a0
	add a1, a1, DISPLAY_TM_TABLE
	sb a2, (a1)
	sb a3, 1(a1)
jr ra

# -------------------------------------------------------------------------------------------------
# Graphics data
# -------------------------------------------------------------------------------------------------

# void display_load_tm_gfx(int* src, int firstDestTile, int numTiles)
#   loads numTiles tiles of graphics into the tilemap graphics area.
#   a0 is the address of the array from which the graphics will be copied.
#   a1 is the first tile in the graphics area that will be overwritten.
#   a2 is the number of tiles to copy. Shouldn't be < 0.
display_load_tm_gfx:
	mul a1, a1, BYTES_PER_TILE
	add a1, a1, DISPLAY_TM_GFX
	mul a2, a2, BYTES_PER_TILE
j PRIVATE_tilecpy

# -------------------------------------------------------------------------------------------------

# void display_load_sprite_gfx(int* src, int firstDestTile, int numTiles)
#   loads numTiles tiles of graphics into the sprite graphics area.
#   a0 is the address of the array from which the graphics will be copied.
#   a1 is the first tile in the graphics area that will be overwritten.
#   a2 is the number of tiles to copy. Shouldn't be < 0.
display_load_sprite_gfx:
	mul a1, a1, BYTES_PER_TILE
	add a1, a1, DISPLAY_SPR_GFX
	mul a2, a2, BYTES_PER_TILE
j PRIVATE_tilecpy

# -------------------------------------------------------------------------------------------------

# PRIVATE FUNCTION, DO NOT CALL!!!!!!!
#  like memcpy, but (src, dest, bytes) instead of (dest, src, bytes).
#  also assumes number of tiles is a nonzero multiple of 4
#  a0 = source
#  a1 = target
#  a2 = number of bytes
PRIVATE_tilecpy:
	_loop:
		lw t0, (a0)
		sw t0, (a1)
		add a0, a0, 4
		add a1, a1, 4
		sub a2, a2, 4
	bgt a2, 0, _loop
	jr ra