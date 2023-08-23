
# -------------------------------------------------------------------------------------------------

# a0 = key to check
# returns 1 if held, 0 if not
display_is_key_held:
	sw a0, DISPLAY_KEY_HELD
	lw v0, DISPLAY_KEY_HELD
jr ra


# -------------------------------------------------------------------------------------------------

# a0 = pointer to palette array
# a1 = start color index
# a2 = number of colors
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