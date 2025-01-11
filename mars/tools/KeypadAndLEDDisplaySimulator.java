package mars.tools;

import javax.swing.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.*;

import mars.Globals;
import mars.mips.hardware.*;
import mars.simulator.Exceptions;

/*
 Copyright (c) 2009 Jose Baiocchi, 2016-2023 Jarrett Billingsley

 Developed by Jose Baiocchi (baiocchi@cs.pitt.edu)
 Modified and greatly extended by Jarrett Billingsley (jarrett@cs.pitt.edu)

 Permission is hereby granted, free of charge, to any person obtaining
 a copy of this software and associated documentation files (the
 "Software"), to deal in the Software without restriction, including
 without limitation the rights to use, copy, modify, merge, publish,
 distribute, sublicense, and/or sell copies of the Software, and to
 permit persons to whom the Software is furnished to do so, subject
 to the following conditions:

 The above copyright notice and this permission notice shall be
 included in all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR
 ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
 CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 (MIT license, http://www.opensource.org/licenses/mit-license.html)
 */
/**
 * LED Display Simulator. It can be run either as a stand-alone Java application
 * having access to the mars package, or through MARS as an item in its Tools
 * menu. It makes maximum use of methods inherited from its abstract superclass
 * AbstractMarsToolAndApplication.
 *
 * @author Jose Baiocchi
 * @author Jarrett Billingsley
 * @version 1.1. 16 February 2010.
 */
public class KeypadAndLEDDisplaySimulator extends AbstractMarsToolAndApplication {
	/*
	Classic mode memory map
	=======================

	0xFFFF0000: DISPLAY_CTRL.w (WO)
	0xFFFF0004: DISPLAY_KEYS.w (RO)
	0xFFFF0008: DISPLAY_BASE.b (WO) - start of user-written LED area
	0xFFFF1007: DISPLAY_END.b-1 (write-only) - end of user-written LED area

	The old implementation had a "secret" buffer from 0xFFFF2008 to 0xFFFF3007 which was
	*technically* accessible by user programs, but no correctly-written program would ever
	do that. The new implementation has no such buffer anymore, so writing to this region
	has no effect (other than making it show a message that you're doing something wrong).

	Enhanced mode memory map
	========================

	MMIO Page  0: global, tilemap control, input, and palette RAM
	MMIO Pages 1-4: framebuffer data
	MMIO Page  5: tilemap table and sprite table
	MMIO Pages 6-9: tilemap graphics
	MMIO Pages A-D: sprite graphics
	MMIO Pages E-F: unused right now

	GLOBAL REGISTERS:

		0xFFFF0000: DISPLAY_CTRL.w           (RW, sets ms/frame and FB/TM enable)
		0xFFFF0004: DISPLAY_SYNC.w           (RW, write to finish frame, read to sleep until next)
		0xFFFF0008: DISPLAY_RESET.w          (WO, clears/resets all graphics RAM to defaults)
		0xFFFF000C: DISPLAY_FRAME_COUNTER.w  (RO, counts how many frame syncs have occurred)

	FRAMEBUFFER REGISTERS:

		0xFFFF0010: DISPLAY_FB_CLEAR.w       (WO, clears framebuffer to color 0 when written)
		0xFFFF0014: DISPLAY_FB_IN_FRONT.w    (WO, 1 puts framebuffer in front of everything else)
		0xFFFF0018: DISPLAY_FB_PAL_OFFS.w    (WO, framebuffer palette offset)
		0xFFFF001C: DISPLAY_FB_SCX.w         (RW, framebuffer X scroll position)
		0xFFFF0020: DISPLAY_FB_SCY.w         (RW, framebuffer Y scroll position)

	TILEMAP REGISTERS:

		0xFFFF0030: DISPLAY_TM_SCX.w         (RW, tilemap X scroll position)
		0xFFFF0034: DISPLAY_TM_SCY.w         (RW, tilemap Y scroll position)

	INPUT REGISTERS:

		0xFFFF0040: DISPLAY_KEY_HELD.w       (RW, write to choose key, read to get state)
		0xFFFF0044: DISPLAY_KEY_PRESSED.w    (RW, write to choose key, read to get state)
		0xFFFF0048: DISPLAY_KEY_RELEASED.w   (RW, write to choose key, read to get state)
		0xFFFF004C: DISPLAY_MOUSE_X.w        (RO, X position of mouse or -1 if mouse not over)
		0xFFFF0050: DISPLAY_MOUSE_Y.w        (RO, Y position of mouse or -1 if mouse not over)
		0xFFFF0054: DISPLAY_MOUSE_HELD.w     (RO, bitflags of mouse buttons held)
		0xFFFF0058: DISPLAY_MOUSE_PRESSED.w  (RO, bitflags of mouse buttons pressed, incl wheel)
		0xFFFF005C: DISPLAY_MOUSE_RELEASED.w (RO, bitflags of mouse buttons released)
		0xFFFF0060: DISPLAY_MOUSE_WHEEL_X.w  (RO, horizontal mouse wheel movement delta)
		0xFFFF0064: DISPLAY_MOUSE_WHEEL_Y.w  (RO, vertical mouse wheel movement delta)
		0xFFFF0068: DISPLAY_MOUSE_VISIBLE.w  (RW, whether or not the mouse cursor is visible)

	PALETTE RAM (RW):

		0xFFFF0C00-0xFFFF0FFF: 256 4B palette entries

	FRAMEBUFFER DATA (RW):

		0xFFFF1000-0xFFFF4FFF: 128x128 (16,384) 1B pixels, each is an index into the palette

	TILEMAP AND SPRITE TABLES (RW):

		0xFFFF5000-0xFFFF57FF: 32x32 2B tilemap entries consisting of (tile, flags)
		0xFFFF5800-0xFFFF5BFF: 256 4B sprite entries consisting of (X, Y, tile, flags)

		0xFFFF5C00-0xFFFF5FFF: 1KB unused space rn

	GRAPHICS DATA (RW):

		0xFFFF6000-0xFFFF9FFF: 256 8x8 1Bpp indexed color tilemap tiles
		0xFFFFA000-0xFFFFDFFF: 256 8x8 1Bpp indexed color sprite tiles

	-- conspicuous blank space from 0xFFFFE000-0xFFFFFFFF that could be used for sound --

	Modes and how to switch
	=======================

		Classic mode

			It starts in classic mode. This mode provides a 64x64-pixel linear framebuffer,
			1 byte per pixel, with a fixed 16-color palette.

			A simple kind of double buffering is used to reduce the likelihood of tearing.
			The back buffer is readable and writable by the user and exists in memory
			in the address range [DISPLAY_BASE .. DISPLAY_END). (In the old implementation,
			the front buffer was *technically* writable by MIPS but well-behaved programs
			would never do this, and the display driver never exposed it.)

			Writing 0 to DISPLAY_CTRL copies the back buffer into the front buffer.

			Writing a 1 to DISPLAY_CTRL copies the back buffer into the front buffer,
			then clears the back buffer to all 0 (black).

			Input is limited to the keyboard arrow keys and Z, X, C, B keys. Input is
			retrieved by reading from DISPLAY_KEYS, which returns the pressed state of
			each key as bitflags.

			As long as only the values 0 and 1 are written to DISPLAY_CTRL, it will stay
			in classic mode.

		Switching modes

			Enhanced mode uses a 128x128 display.

			Writing a value >= 257 (0x101) to DISPLAY_CTRL will switch into enhanced mode.

			The value written to DISPLAY_CTRL is a bitfield:
				low 2 bits are enables:
					00: undefined
					01: framebuffer on
					10: tilemap on
					11: framebuffer and tilemap on

				bit 8 has no specific meaning but setting it along with the enables switches
				to enhanced mode.

				bits 16-23 are the milliseconds per frame used by DISPLAY_SYNC, but limited to
				the range [10, 100]. (I guess technically it only needs bits 16-22 but oh well)

				bits 9-15 and 24-31 are undefined atm

				long story short, set DISPLAY_CTRL to:
					(ms_per_frame << 16) | 0x100 | mode

			There is no way to switch back to classic mode, short of closing the display window
			and reopening it. (Why would you ever need to? That mode exists for backwards
			compatibility only.)

	Resetting
	=========

		Because MARS doesn't actually notify tools that a program was assembled/started running,
		there is a DISPLAY_RESET register. Writing any value to this register clears out all
		state associated with the display - all graphics RAM, display options, etc. - except
		for the current graphics mode and milliseconds per frame.

		Typically you'd do this immediately after switching into enhanced mode with DISPLAY_CTRL.

	Palette
	=======

		There is a single global 256-entry palette. Each palette entry is 4 bytes, and is an
		RGB888 color with 1 byte unused. In memory the byte order is [BB, GG, RR, 00], which,
		because virtually every computer today is little-endian and therefore so is MARS, means
		that a color is represented as a word in the format 0x00RRGGBB. (Let me know if you
		ever run this plugin on a big-endian machine and run into problems.)

		The framebuffer, tilemap, and sprites all share the same palette, but they all have
		"palette offset" features to allow you to split up the palette into different regions.

		When DISPLAY_RESET is written to, the palette is initialized with some useful default
		colors so that you can get to drawing stuff to the screen right away.

	Background Color
	================

		Palette entry 0 is special as it specifies the background color. This color will "show
		through" pixels with a color index of 0 in the framebuffer, tilemap, and sprites (as long
		as there is no opaque pixel behind them.)

	Framebuffer
	===========

		The framebuffer is a 128x128 linear framebuffer, 1 byte per pixel, covering addresses
		0xFFFF1000 to 0xFFFF4FFF inclusive.

		Each pixel is a color index into the global palette. Any pixels with color index 0 will
		show the background color through them. Writing any value to DISPLAY_FB_CLEAR will fill
		the entire framebuffer with color index 0.

		The DISPLAY_FB_PAL_OFFS register controls the framebuffer's palette offset. This is added
		to every non-zero pixel's color index (mod 256) before fetching the colors from the
		palette. E.g. if a pixel is color index 16, and the palette offset is 3, then the pixel
		will be drawn with global palette entry 16 + 3 = 19.

		This not only lets you set aside one part of the palette for use by the framebuffer, but
		it lets you do Fun Palette Shifting Effects on the framebuffer as well.

		Last, the framebuffer can be scrolled freely at pixel resolution on both axes. The
		scroll amount can be written as a signed integer but will be interpreted modulo 127.
		The framebuffer wraps around at the edges. Since the framebuffer is the same size as
		the display itself, this won't let you do "infinite scrolling" any faster than one pixel
		at a time, but it might be nice for effects.

	Tilemap
	=======

		The tilemap is a 32x32-tile grid, where each tile is 8x8 pixels, for a total of 256x256
		pixels (4 full screens). There are two parts of the tilemap: the table, which specifies
		which tile is used for each of the 1024 locations; and the graphics, which is the actual
		pixel data for the 8x8 tiles.

		The tilemap table covers addresses 0xFFFF5000 to 0xFFFF57FF inclusive. Each of the 1024
		tiles has a 2-byte entry consisting of [tile_index, flags] in that order.

		For each tile, graphics are fetched from (0xFFFF6000 + tile_index * 64).

		The tile flags are a bitfield: PPPPxHVO
			O = priority (appears over sprites)
			V = vertical flip
			H = horizontal flip
			PPPP = palette row index
				this value is multiplied by 16 and added to every nonzero color index in this
				tile's graphics, similar to the framebuffer palette offset but with less precision.

		Because of the priority flag on each tile, you can think of there being two "visual" layers
		of the tilemap, one on either side of the sprites.

		The tilemap graphics cover addresses 0xFFFF6000 to 0xFFFF9FFF inclusive. This is enough
		space for 256 8x8-pixel tiles. Each tile is 8x8 = 64 pixels, stored top-to-bottom in row-
		major order. Each pixel is 1 byte and is a color index into the global palette, just like
		the framebuffer.

		The tilemap can also be scrolled freely at pixel resolution on both axes. The scroll
		amount can be written as a signed integer but will be interpreted modulo 256. The tilemap
		wraps around at the edges, so if you see past the right side, you will see the tiles on
		the left side, etc.

	Sprites
	=======

		Sprites are independently movable images that can be placed at any pixel coordinate,
		onscreen or partially offscreen. There can be up to 256 sprites.

		Like the tilemap, there are two parts to sprites: the table, which specifies per-sprite
		attributes, and the graphics area.

		The sprite table covers addresses 0xFFFF5800 to 0xFFFF5BFF inclusive. Each of the 256
		sprites has a 4-byte entry consisting of [X, Y, tile_index, flags] in that order.

		Each sprite is positioned from its top-left corner. So an 8x8 sprite at position 10, 10
		will cover a rectangle of pixels from 10, 10 to 17, 17.

		X and Y are signed. This allows you to place sprites "off the sides" of the screen, so
		that they can enter and exit the screen smoothly.

		For each sprite, graphics are fetched starting at (0xFFFFA000 + tile_index * 64).

		The flags are a bitfield: PPPPSHVE
			E = enable (1 for visible)
			V = vertical flip
			H = horizontal flip
			S = size (0 = 8x8 (1 tile), 1 = 16x16 (4 tiles))
				for 16x16 sprites, tiles are fetched sequentially from the graphics area,
				and are put onscreen in this order:
					1 2
					3 4
				horizontal and vertical flipping flags apply to the *entire* 16x16 sprite,
				not the individual tiles that make it up.
			PPPP = palette row index
				this value is multiplied by 16 and added to every nonzero color index in this
				sprite's graphics, similar to the framebuffer and tilemap palette offsets but
				with less precision.

		Sprite priority is by order in the list. Sprite 0 appears on top of sprite 1, which
		appears on top of sprite 2, etc. So you can think of the sprites being drawn from 255
		down to 0.

		There are no "per-scanline limits" on the number of visible sprites.

		Sprite graphics are stored exactly the same way as tilemap graphics.

	Finishing a frame
	=================

		To actually display anything to the screen, you must store a value into DISPLAY_SYNC. The
		value is ignored; the act of storing is what tells the display, "I'm finished with this
		frame."

		Finishing the frame first composites all the graphical elements into the display.

		If DISPLAY_FB_IN_FRONT is 0 (the default), the display elements are drawn from back
		(first drawn) to front (last drawn) like so:

			- Background color
			- Framebuffer (if enabled)
			- Tilemap tiles without priority (if enabled)
			- Any enabled sprites, from sprite 255 down to sprite 0
			- Tilemap tiles with priority (if enabled)

		If DISPLAY_FB_IN_FRONT is set to 1, the framebuffer is instead drawn last, on top of
		everything else.

		Finishing the frame also updates the input subsystem.

	Framerate synchronization
	=========================

		After finishing a frame, the next thing you should do is to load from DISPLAY_SYNC. Again
		the value that is loaded is meaningless; the act of loading is what triggers framerate
		synchronization.

		Loading from DISPLAY_SYNC will cause your program to sleep for a variable amount of time,
		in an attempt to make every frame take the same amount of time (because the length of
		processing between frames can vary). The length of a frame is set by bits 16-23 of
		DISPLAY_CTRL (remember that?). E.g. if that value is 16, then each frame will be ~16
		milliseconds long, and you will get ~60 frames per second.

	Input
	=====

		There are many many keyboard keys. Instead of packing them all into a big unwieldy
		bitmap array, you tell the plugin which key you want to test, and then you get a response.

		DISPLAY_KEY_HELD is the register for asking if a key is held down. For example, if you want
		to know if the A key is held down, store KEY_A into DISPLAY_KEY_HELD, then load from
		DISPLAY_KEY_HELD; the loaded value will be 1 for "being held" and 0 for "not held."

		DISPLAY_KEY_PRESSED works similarly, but returns a 1 only on the first frame that a key
		was pressed; and DISPLAY_KEY_RELEASED returns a 1 only on the first frame that a key was
		released.

		The mouse can also be used. DISPLAY_MOUSE_X and DISPLAY_MOUSE_Y contain the mouse's
		coordinates on the display in the range 0 to 127 each; or -1 if the mouse is not over
		the display. It is sufficient to check if just one of them is -1; either they are both
		-1 or neither is, because these are only updated when you write to DISPLAY_SYNC.

		DISPLAY_MOUSE_HELD, DISPLAY_MOUSE_PRESSED, and DISPLAY_MOUSE_RELEASED are read-only
		bitmap arrays. There are constants you can use to extract individual buttons from these
		values.

		Finally DISPLAY_MOUSE_WHEEL_X/Y are read-only integer values. DISPLAY_MOUSE_WHEEL_Y is
		for vertical scroll wheel movements (the more common kind); DISPLAY_MOUSE_WHEEL_X is for
		horizontal scroll wheel movements (less common, but common on trackpads).

		For both of these registers, 0 means the wheel has not moved. Positive values mean
		scrolling up or to the right; negative values mean scrolling down or to the left.

		These also work with trackpads, and there's some fancy stuff going on in Java that
		respects user preferences and so on. The magnitudes of these values depend on how fast
		they are scrolling, too, it's not just "-1 or +1".
	*/

	// --------------------------------------------------------------------------------------------
	// Constants

	static final long serialVersionUID = 1; // To eliminate a warning about serializability.

	private static final String version = "Version 2";
	private static final String title = "Keypad and LED Display MMIO Simulator";
	private static final String heading = "Classic Mode";
	private static final String enhancedHeading = "Enhanced Mode";

	// Technically the base address is not fixed and could change based on memory
	// configuration. But the two nonstandard configurations have only 256 *bytes*
	// of MMIO space, which is nowhere near enough for us, and we never use it anyway,
	// so let's just hardcode it! Fuck it!
	private static final int MMIO_BASE = 0xFFFF0000;
	private static final int DISPLAY_CTRL = MMIO_BASE;
	private static final int ENHANCED_MODE_SWITCH_VALUE = 257; // 0x101

	// --------------------------------------------------------------------------------------------
	// Instance fields

	private JPanel panel;
	private JCheckBox gridCheckBox;
	private JCheckBox zoomCheckBox;
	private LEDDisplayPanel displayPanel;
	private ClassicLEDDisplayPanel classicDisplay;
	private EnhancedLEDDisplayPanel enhancedDisplay;
	private boolean isEnhanced = false;

	// --------------------------------------------------------------------------------------------
	// Standalone main

	public static void main(String[] args) {
		new KeypadAndLEDDisplaySimulator(title + " stand-alone, " + version, heading).go();
	}

	// --------------------------------------------------------------------------------------------
	// AbstractMarsToolAndApplication implementation

	public KeypadAndLEDDisplaySimulator(String title, String heading) {
		super(title, heading);
	}

	public KeypadAndLEDDisplaySimulator() {
		super(title + ", " + version, heading);
	}

	@Override
	public String getName() {
		return "Keypad and LED Display Simulator";
	}

	/** Builds the actual GUI for the tool. */
	@Override
	protected JComponent buildMainDisplayArea() {
		panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		classicDisplay = new ClassicLEDDisplayPanel(this);
		enhancedDisplay = new EnhancedLEDDisplayPanel(this);

		displayPanel = classicDisplay;

		JPanel subPanel = new JPanel();
		gridCheckBox = new JCheckBox("Show Grid Lines");
		gridCheckBox.addItemListener((e) -> {
			displayPanel.setGridLinesEnabled(e.getStateChange() == ItemEvent.SELECTED);
			displayPanel.revalidate();
			this.theWindow.pack();
			displayPanel.repaint();
			displayPanel.requestFocusInWindow();
		});
		subPanel.add(gridCheckBox);

		zoomCheckBox = new JCheckBox("Zoom");
		zoomCheckBox.addItemListener((e) -> {
			displayPanel.setZoomed(e.getStateChange() == ItemEvent.SELECTED);
			displayPanel.revalidate();
			this.theWindow.pack();
			displayPanel.repaint();
			displayPanel.requestFocusInWindow();
		});
		subPanel.add(zoomCheckBox);

		panel.add(subPanel);
		panel.add(displayPanel);

		displayPanel.requestFocusInWindow();

		return panel;
	}

	/** Called after the GUI has been constructed. */
	@Override
	protected void initializePostGUI() {
		// force a repaint when the connect button is clicked
		connectButton.addActionListener((e) -> {
			displayPanel.repaint();
		});

		// no resizable!
		JDialog dialog = (JDialog)this.theWindow;

		if(dialog != null) {
			dialog.setResizable(false);
		}

		// make it so if the window gets focus, focus the display, so it can get events
		theWindow.addWindowFocusListener(new WindowAdapter() {
			public void windowGainedFocus(WindowEvent e) {
				displayPanel.requestFocusInWindow();
			}
		});

		/*

		System.out.println("double-buffered? " + theWindow.isDoubleBuffered());

		// must call this so the call to createBufferStrategy succeeds
		theWindow.pack();

		// set up for double-buffering
		try {
			theWindow.createBufferStrategy(2);
		} catch(Exception e) {
			System.err.println("ERROR: couldn't set up double-buffering: " + e);
		}

		var strategy = theWindow.getBufferStrategy();
		System.out.println("Strategy: " + strategy);
		System.out.println("double-buffered? " + theWindow.isDoubleBuffered());
		var caps = strategy.getCapabilities();
		System.out.println("BB: " + caps.getBackBufferCapabilities().isTrueVolatile());
		System.out.println("FB: " + caps.getFrontBufferCapabilities().isTrueVolatile());
		System.out.println("FC: " + caps.getFlipContents());
		*/

		// TODO: experiment with driving painting from a separate thread instead of
		// relying on repaint events
	}

	/** Called when the Connect button is clicked, to hook it into the memory subsystem. */
	@Override
	protected void addAsObserver() {
		// end address has to be the address of the last *word* observed
		int endAddress = Memory.memoryMapLimitAddress - Memory.WORD_LENGTH_BYTES + 1;
		addAsObserver(Memory.memoryMapBaseAddress, endAddress);
	}

	/** Called when the Reset button is clicked. */
	@Override
	protected void reset() {
		displayPanel.reset();
		displayPanel.requestFocusInWindow();
		updateDisplay();
	}

	/** Used to watch for writes to control registers. */
	@Override
	protected void processMIPSUpdate(Observable memory, AccessNotice accessNotice) {
		MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;

		if(notice.getAccessType() == AccessNotice.WRITE) {
			if(notice.getAddress() == DISPLAY_CTRL) {
				int value = notice.getValue();

				if(value >= ENHANCED_MODE_SWITCH_VALUE) {
					this.switchToEnhancedMode();
				}

				this.displayPanel.writeToCtrl(value);
			} else {
				this.displayPanel.handleWrite(
					notice.getAddress(), notice.getLength(), notice.getValue());
			}
		} else {
			this.displayPanel.handleRead(
				notice.getAddress(), notice.getLength(), notice.getValue());
		}
	}

	/** Called any time an MMIO access is made. */
	@Override
	protected void updateDisplay() {
		displayPanel.repaintIfNeeded();
	}

	// --------------------------------------------------------------------------------------------
	// Memory helpers

	private boolean okayToWriteToMemory() {
		return !this.isBeingUsedAsAMarsTool || (
			this.connectButton != null && this.connectButton.isConnected());
	}

	private void writeWordToMemory(int addr, int value) {
		if(okayToWriteToMemory()) {
			Globals.memory.getMMIOPage((addr >> 12) & 0xF)[(addr - MMIO_BASE) / 4] = value;
		}
		// else {
		// 	System.err.printf("Couldn't write 0x%08X to 0x%08X\n", value, addr);
		// 	System.err.println(this.isBeingUsedAsAMarsTool + ", " + this.connectButton + ", " +
		// 		(this.connectButton != null ? this.connectButton.isConnected() : "x"));
		// }
	}

	private void fillMemory(int startAddr, int endAddr, int fillValue) {
		int startPage = (startAddr >> 12) & 0xF;
		int endPage = ((endAddr - 4) >> 12) & 0xF;

		if(startPage == endPage) {
			int startOffset = (startAddr & 0xFFF) / 4;
			int numWords = (endAddr - startAddr) / 4;
			int endOffset = startOffset + numWords;
			Arrays.fill(Globals.memory.getMMIOPage(startPage), startOffset, endOffset, fillValue);
		} else {
			System.err.println(String.format(
				"fillMemory bad addresses (0x%08X to 0x%08X), fillValue = 0x%08X\n",
				startAddr,
				endAddr,
				fillValue));
		}
	}

	// --------------------------------------------------------------------------------------------
	// Mode switching

	private void switchToEnhancedMode() {
		if(!isEnhanced) {
			isEnhanced = true;

			panel.remove(classicDisplay);
			panel.add(enhancedDisplay);
			displayPanel = enhancedDisplay;

			gridCheckBox.setSelected(false);
			gridCheckBox.setEnabled(false);
			displayPanel.setZoomed(zoomCheckBox.isSelected());

			if(this.isBeingUsedAsAMarsTool)
				headingLabel.setText(enhancedHeading);

			displayPanel.revalidate();
			this.theWindow.pack();
			displayPanel.repaint();
		}
	}

	// --------------------------------------------------------------------------------------------
	// Common base class for both kinds of displays

	private static abstract class LEDDisplayPanel extends JPanel {
		protected KeypadAndLEDDisplaySimulator sim;

		protected Font bigFont = new Font("Sans-Serif", Font.BOLD, 24);

		protected boolean haveFocus = false;
		protected boolean shouldRepaint = true;
		protected boolean drawGridLines = false;
		protected boolean zoomed = false;

		protected final int nColumns;
		protected final int nRows;
		protected final int cellDefaultSize;
		protected final int cellZoomedSize;
		protected int cellSize;
		protected int cellPadding = 0;
		protected int pixelSize;
		protected int displayWidth;
		protected int displayHeight;

		public LEDDisplayPanel(KeypadAndLEDDisplaySimulator sim,
			int nColumns, int nRows, int cellDefaultSize, int cellZoomedSize) {
			this.sim = sim;
			this.setFocusable(true);
			this.setFocusTraversalKeysEnabled(false);
			this.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					if(!haveFocus) {
						requestFocusInWindow();
					}
				}
			});

			this.addFocusListener(new FocusListener() {
				public void focusGained(FocusEvent e) {
					haveFocus = true;
					repaint();
				}

				public void focusLost(FocusEvent e) {
					haveFocus = false;
					repaint();
				}
			});

			this.nColumns = nColumns;
			this.nRows = nRows;
			this.cellDefaultSize = cellDefaultSize;
			this.cellZoomedSize = cellZoomedSize;
			this.recalcSizes();
		}

		protected void recalcSizes() {
			cellSize      = zoomed ? cellZoomedSize : cellDefaultSize;
			cellPadding   = drawGridLines ? 1 : 0;
			pixelSize     = cellSize - cellPadding;
			pixelSize     = cellSize - cellPadding;
			displayWidth  = (nColumns * cellSize);
			displayHeight = (nRows * cellSize);
			this.setPreferredSize(new Dimension(displayWidth, displayHeight));
		}

		public void setGridLinesEnabled(boolean e) {
			if(e != this.drawGridLines) {
				this.drawGridLines = e;
				this.recalcSizes();
			}
		}

		public void setZoomed(boolean e) {
			if(e != this.zoomed) {
				this.zoomed = e;
				this.recalcSizes();
			}
		}

		public void setShouldRepaint(boolean b) {
			this.shouldRepaint = b;
		}

		public void repaintIfNeeded() {
			if(this.shouldRepaint) {
				this.shouldRepaint = false;
				this.repaint();
			}
		}

		@Override
		public void paintComponent(Graphics g) {
			if(!sim.connectButton.isConnected()) {
				g.setColor(Color.BLACK);
				g.fillRect(0, 0, displayWidth, displayHeight);
				g.setColor(Color.RED);
				g.setFont(bigFont);
				g.drawString("vvvv CLICK THE CONNECT BUTTON!", 10, displayHeight - 10);
			} else {
				this.paintDisplay(g);

				if(!haveFocus) {
					g.setColor(new Color(0, 0, 0, 127));
					g.fillRect(0, 0, displayWidth, displayHeight);
					g.setColor(Color.YELLOW);
					g.setFont(bigFont);
					g.drawString("Click here to interact.", 10, displayHeight / 2);
				}
			}
		}

		public abstract void reset();
		public abstract void writeToCtrl(int value);
		public abstract void handleWrite(int addr, int length, int value);
		public abstract void handleRead(int addr, int length, int value);
		protected abstract void paintDisplay(Graphics g);
	}

	// --------------------------------------------------------------------------------------------
	// Classic display

	/** The classic 64x64 graphical display with 8-key input. */
	private static class ClassicLEDDisplayPanel extends LEDDisplayPanel {
		private static final int N_COLUMNS = 64;
		private static final int N_ROWS = 64;
		private static final int CELL_DEFAULT_SIZE = 8;
		private static final int CELL_ZOOMED_SIZE = 12;

		private static final int KEY_U = 1;
		private static final int KEY_D = 2;
		private static final int KEY_L = 4;
		private static final int KEY_R = 8;
		private static final int KEY_B = 16;
		private static final int KEY_Z = 32;
		private static final int KEY_X = 64;
		private static final int KEY_C = 128;

		private static final int DISPLAY_KEYS = 0xFFFF0004;
		private static final int DISPLAY_BASE = 0xFFFF0008;
		private static final int DISPLAY_SIZE = N_ROWS * N_COLUMNS; // bytes
		private static final int DISPLAY_END  = DISPLAY_BASE + DISPLAY_SIZE;
		private static final int DISPLAY_SPLIT = 0xFFFF1000;

		private static final int COLOR_MASK = 15;

		/** color palette. */
		private static final int[][] PixelColors = new int[][] {
			{0, 0, 0},       // black
			{255, 0, 0},     // red
			{255, 127, 0},   // orange
			{255, 255, 0},   // yellow
			{0, 255, 0},     // green
			{51, 102, 255},  // blue
			{255, 0, 255},   // magenta
			{255, 255, 255}, // white

			// extended colors!
			{63, 63, 63},    // dark grey
			{127, 0, 0},     // brick
			{127, 63, 0},    // brown
			{192, 142, 91},  // tan
			{0, 127, 0},     // dark green
			{25, 50, 127},   // dark blue
			{63, 0, 127},    // purple
			{127, 127, 127}, // light grey
		};

		private boolean doingSomethingWeird = false;
		private int keyState = 0;
		private BufferedImage image =
			new BufferedImage(N_COLUMNS, N_ROWS, BufferedImage.TYPE_INT_RGB);

		public ClassicLEDDisplayPanel(KeypadAndLEDDisplaySimulator sim) {
			super(sim, N_COLUMNS, N_ROWS, CELL_DEFAULT_SIZE, CELL_ZOOMED_SIZE);

			this.addKeyListener(new KeyAdapter() {
				public void keyPressed(KeyEvent e) {
					switch(e.getKeyCode()) {
						case KeyEvent.VK_LEFT:  changeKeyState(keyState | KEY_L); break;
						case KeyEvent.VK_RIGHT: changeKeyState(keyState | KEY_R); break;
						case KeyEvent.VK_UP:    changeKeyState(keyState | KEY_U); break;
						case KeyEvent.VK_DOWN:  changeKeyState(keyState | KEY_D); break;
						case KeyEvent.VK_B:     changeKeyState(keyState | KEY_B); break;
						case KeyEvent.VK_Z:     changeKeyState(keyState | KEY_Z); break;
						case KeyEvent.VK_X:     changeKeyState(keyState | KEY_X); break;
						case KeyEvent.VK_C:     changeKeyState(keyState | KEY_C); break;
						default: break;
					}
				}

				public void keyReleased(KeyEvent e) {
					switch(e.getKeyCode()) {
						case KeyEvent.VK_LEFT:  changeKeyState(keyState & ~KEY_L); break;
						case KeyEvent.VK_RIGHT: changeKeyState(keyState & ~KEY_R); break;
						case KeyEvent.VK_UP:    changeKeyState(keyState & ~KEY_U); break;
						case KeyEvent.VK_DOWN:  changeKeyState(keyState & ~KEY_D); break;
						case KeyEvent.VK_B:     changeKeyState(keyState & ~KEY_B); break;
						case KeyEvent.VK_Z:     changeKeyState(keyState & ~KEY_Z); break;
						case KeyEvent.VK_X:     changeKeyState(keyState & ~KEY_X); break;
						case KeyEvent.VK_C:     changeKeyState(keyState & ~KEY_C); break;
						default: break;
					}
				}
			});
		}

		/** set the key state to the new state, and update the value in MIPS memory
		for the program to be able to read. */
		private void changeKeyState(int newState) {
			keyState = newState;

			synchronized(Globals.memoryAndRegistersLock) {
				sim.writeWordToMemory(DISPLAY_KEYS, newState);
			}
		}

		/** quickly clears the graphics memory to 0 (black). */
		@Override
		public void reset() {
			if(!Globals.inputSyscallLock.tryLock()) {
				JOptionPane.showMessageDialog(
					null, // parent
					"You cannot reset this tool while an input syscall is in progress.",
					null, // title,
					JOptionPane.ERROR_MESSAGE);
				return;
			}

			try {
				this.resetGraphicsMemory(true);
				this.setShouldRepaint(true);
			} finally {
				Globals.inputSyscallLock.unlock();
			}
		}

		@Override
		public void writeToCtrl(int value) {
			// Copy values from memory to internal buffer, reset if we must.
			this.updateImage();

			if(value != 0)
				this.resetGraphicsMemory(false);

			this.setShouldRepaint(true);
		}

		@Override
		public void handleWrite(int addr, int length, int value) {
			if(addr >= DISPLAY_END) {
				doingSomethingWeird = true;
			}
		}

		@Override
		public void handleRead(int addr, int length, int value) {
			// do NOTHING
		}

		private void resetGraphicsMemory(boolean clearBuffer) {
			synchronized(Globals.memoryAndRegistersLock) {
				sim.fillMemory(DISPLAY_BASE, DISPLAY_SPLIT, 0);
				sim.fillMemory(DISPLAY_SPLIT, DISPLAY_END, 0);

				if(clearBuffer) {
					this.clearImage();
				}
			}
		}

		// grab values out of RAM and turn them into image pixels
		private void updateImage() {
			synchronized(image) {
				synchronized(Globals.memoryAndRegistersLock) {
					int[] page = Globals.memory.getMMIOPage(0);
					int ptr = 2;
					var r = image.getRaster();

					for(int row = 0; row < N_ROWS; row++) {
						for(int col = 0; col < N_COLUMNS; col += 4, ptr++) {
							// hacky, but.
							if(ptr == (DISPLAY_SPLIT & 0xFFFF) / 4) {
								ptr = 0;
								page = Globals.memory.getMMIOPage(1);
							}

							int pixel = page[ptr];

							r.setPixel(col, row, PixelColors[pixel & COLOR_MASK]);
							r.setPixel(col + 1, row, PixelColors[(pixel >> 8) & COLOR_MASK]);
							r.setPixel(col + 2, row, PixelColors[(pixel >> 16) & COLOR_MASK]);
							r.setPixel(col + 3, row, PixelColors[(pixel >> 24) & COLOR_MASK]);
						}
					}
				}
			}
		}

		// Clear the image to black
		private void clearImage() {
			var g = image.createGraphics();
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, image.getWidth(), image.getHeight());
			g.dispose();
		}

		@Override
		protected void paintDisplay(Graphics g) {
			synchronized(image) {
				g.drawImage(image, 0, 0, displayWidth, displayHeight, null);
			}

			if(drawGridLines) {
				g.setColor(Color.GRAY);

				for(int col = 0; col < N_COLUMNS; col++) {
					int x = col * cellSize;
					g.drawLine(x, 0, x, displayHeight);
				}

				for(int row = 0; row < N_ROWS; row++) {
					int y = row * cellSize;
					g.drawLine(0, y, displayWidth, y);
				}
			}

			if(doingSomethingWeird) {
				g.setColor(new Color(0, 0, 0, 127));
				g.fillRect(0, 0, displayWidth, displayHeight);
				g.setColor(Color.YELLOW);
				g.setFont(bigFont);
				g.drawString("This window needs to be open and", 10, 40);
				g.drawString("connected before you run your", 10, 70);
				g.drawString("program. Stop the program, hit", 10, 100);
				g.drawString("assemble, and hit run.", 10, 130);
			}
		}
	}

	// --------------------------------------------------------------------------------------------
	// Enhanced display

	/** The new, enhanced display. */
	private static class EnhancedLEDDisplayPanel extends LEDDisplayPanel {
		// Debugging!
		private static final boolean DEBUG_OVERLAY = false;

		// Register addresses
		private static final int DISPLAY_SYNC           = 0xFFFF0004;
		private static final int DISPLAY_RESET          = 0xFFFF0008;
		private static final int DISPLAY_FRAME_COUNTER  = 0xFFFF000C;
		private static final int DISPLAY_FB_CLEAR       = 0xFFFF0010;
		private static final int DISPLAY_FB_IN_FRONT    = 0xFFFF0014;
		private static final int DISPLAY_FB_PAL_OFFS    = 0xFFFF0018;
		private static final int DISPLAY_FB_SCX         = 0xFFFF001C;
		private static final int DISPLAY_FB_SCY         = 0xFFFF0020;
		private static final int DISPLAY_TM_SCX         = 0xFFFF0030;
		private static final int DISPLAY_TM_SCY         = 0xFFFF0034;
		private static final int DISPLAY_KEY_HELD       = 0xFFFF0040;
		private static final int DISPLAY_KEY_PRESSED    = 0xFFFF0044;
		private static final int DISPLAY_KEY_RELEASED   = 0xFFFF0048;
		private static final int DISPLAY_MOUSE_X        = 0xFFFF004C;
		private static final int DISPLAY_MOUSE_Y        = 0xFFFF0050;
		private static final int DISPLAY_MOUSE_HELD     = 0xFFFF0054;
		private static final int DISPLAY_MOUSE_PRESSED  = 0xFFFF0058;
		private static final int DISPLAY_MOUSE_RELEASED = 0xFFFF005C;
		private static final int DISPLAY_MOUSE_WHEEL_X  = 0xFFFF0060;
		private static final int DISPLAY_MOUSE_WHEEL_Y  = 0xFFFF0064;
		private static final int DISPLAY_MOUSE_VISIBLE  = 0xFFFF0068;
		private static final int DISPLAY_PALETTE_RAM    = 0xFFFF0C00;
		private static final int DISPLAY_FB_RAM         = 0xFFFF1000;
		private static final int DISPLAY_TM_TABLE       = 0xFFFF5000;
		private static final int DISPLAY_SPR_TABLE      = 0xFFFF5800;
		private static final int DISPLAY_SPR_TABLE_END  = 0xFFFF5BFF;
		private static final int DISPLAY_TM_GFX         = 0xFFFF6000;
		private static final int DISPLAY_SPR_GFX        = 0xFFFFA000;

		// Mode bits
		private static final int MODE_FB_ENABLE = 1;
		private static final int MODE_TM_ENABLE = 2;
		private static final int MODE_MASK = 3;

		// Framerate
		private static final int MS_PER_FRAME_SHIFT = 16;
		private static final int MS_PER_FRAME_MASK = 0xFF;
		private static final int MIN_MS_PER_FRAME = 10;
		private static final int MAX_MS_PER_FRAME = 100;

		// Display and framebuffer constants
		private static final int N_COLUMNS = 128;
		private static final int N_ROWS = 128;
		private static final int CELL_DEFAULT_SIZE = 4;
		private static final int CELL_ZOOMED_SIZE = 6;
		private static final int FB_SCX_MASK = N_COLUMNS - 1;
		private static final int FB_SCY_MASK = N_ROWS - 1;

		// Tile graphics constants
		private static final int TILE_W = 8;
		private static final int TILE_H = 8;
		private static final int BYTES_PER_TILE = TILE_W * TILE_H;

		// Tilemap constants
		private static final int TM_ENTRY_SIZE = 2;
		private static final int N_TM_COLUMNS = 32;
		private static final int N_TM_ROWS = 32;
		private static final int TM_PIXEL_W = N_TM_COLUMNS * TILE_W;
		private static final int TM_PIXEL_H = N_TM_ROWS * TILE_H;
		private static final int N_TM_GFX_TILES = 256;
		private static final int TM_SCX_MASK = TM_PIXEL_W - 1;
		private static final int TM_SCY_MASK = TM_PIXEL_H - 1;

		// Sprite constants
		private static final int N_SPRITES = 256;
		private static final int SPRITE_ENTRY_SIZE = 4;
		private static final int N_SPR_GFX_TILES = 256;
		private static final int SPRITE_LARGE_W = 16;
		private static final int SPRITE_LARGE_H = 16;

		// Tilemap/sprite flag constants
		private static final int BIT_PRIORITY = 1; // for tiles
		private static final int BIT_ENABLE = 1; // for sprites
		private static final int BIT_VFLIP = 2; // tiles + sprites
		private static final int BIT_HFLIP = 4; // tiles + sprites
		private static final int BIT_SIZE = 8; // sprites

		// Mouse button constants
		private static final int MOUSE_LBUTTON = 1;
		private static final int MOUSE_RBUTTON = 2;
		private static final int MOUSE_MBUTTON = 4;

		// ----------------------------------------------------------------------------------------
		// Instance variables

		// Debug stuff
		private long dbg_avgFrameLength = 0;

		// Input stuff
		private int mouseX = -1;
		private int mouseY = -1;
		private int mouseButtons = 0;
		private int lastMouseButtons = 0;
		private int mouseWheelX = 0;
		private int mouseWheelY = 0;
		private boolean mouseOver = false;

		// Sets of keys. Since there are unlikely to be more than a few keys held
		// down or changing per frame, a capacity of 16 should be more than sufficient.
		private Set<Integer> keysHeld = Collections.synchronizedSet(new HashSet<>(16));
		private Set<Integer> keysPressed = Collections.synchronizedSet(new HashSet<>(16));
		private Set<Integer> keysReleased = Collections.synchronizedSet(new HashSet<>(16));

		// DISPLAY_CTRL
		private int msPerFrame = 16;
		private long lastFrameTime = 0;
		private boolean fbEnabled = false;
		private boolean tmEnabled = false;
		private int frameCounter = 0;

		// Framebuffer registers
		private boolean fbInFront = false;
		private int fbPalOffs = 0;
		private int fbScx = 0;
		private int fbScy = 0;

		// Tilemap registers
		private int tmScx = 0;
		private int tmScy = 0;

		// Palette RAM (0 is red, 1 is green, 2 is blue)
		private byte[][] paletteRam = new byte[3][256];

		// Framebuffer RAM
		private byte[] fbRam = new byte[N_COLUMNS * N_ROWS];

		// Tilemap table and graphics
		private byte[] tmTable = new byte[N_TM_COLUMNS * N_TM_ROWS * TM_ENTRY_SIZE];
		private byte[] tmGraphics = new byte[N_TM_GFX_TILES * TILE_W * TILE_H];

		// Sprite table and graphics
		private byte[] sprTable = new byte[N_SPRITES * SPRITE_ENTRY_SIZE];
		private byte[] sprGraphics = new byte[N_SPR_GFX_TILES * TILE_W * TILE_H];

		// Dirty flags (set to true if things are changed, forcing a redraw of those layers)
		private boolean isPalDirty = true;
		private boolean isFbDirty = true;
		private boolean isTmDirty = true;
		private boolean isSprDirty = true;

		// Intermediate images
		private WritableRaster fullTmLayerLo =
			Raster.createBandedRaster(DataBuffer.TYPE_BYTE, TM_PIXEL_W, TM_PIXEL_H, 1, null);
		private WritableRaster fullTmLayerHi =
			Raster.createBandedRaster(DataBuffer.TYPE_BYTE, TM_PIXEL_W, TM_PIXEL_H, 1, null);

		// Big enough to accommodate the largest sprites partially offscreen on all 4 sides
		private WritableRaster fullSpriteLayer =
			Raster.createBandedRaster(DataBuffer.TYPE_BYTE,
				N_COLUMNS + (2 * SPRITE_LARGE_W), N_ROWS + (2 * SPRITE_LARGE_H), 1, null);

		// Compositing layers
		private WritableRaster fbLayer =
			Raster.createBandedRaster(DataBuffer.TYPE_BYTE, N_COLUMNS, N_ROWS, 1, null);
		private WritableRaster tmLayerLo =
			Raster.createBandedRaster(DataBuffer.TYPE_BYTE, N_COLUMNS, N_ROWS, 1, null);
		private WritableRaster tmLayerHi =
			Raster.createBandedRaster(DataBuffer.TYPE_BYTE, N_COLUMNS, N_ROWS, 1, null);
		private WritableRaster spriteLayer =
			Raster.createBandedRaster(DataBuffer.TYPE_BYTE, N_COLUMNS, N_ROWS, 1, null);
		private WritableRaster finalRaster =
			Raster.createBandedRaster(DataBuffer.TYPE_BYTE, N_COLUMNS, N_ROWS, 1, null);
		private BufferedImage finalImage =
			new BufferedImage(N_COLUMNS, N_ROWS, BufferedImage.TYPE_INT_RGB);

		private Object finalImageLock = new Object();

		// ----------------------------------------------------------------------------------------
		// Constructor

		public EnhancedLEDDisplayPanel(KeypadAndLEDDisplaySimulator sim) {
			super(sim, N_COLUMNS, N_ROWS, CELL_DEFAULT_SIZE, CELL_ZOOMED_SIZE);
			this.initializePaletteRam();

			this.addKeyListener(new KeyListener() {
				public void keyTyped(KeyEvent e) {
					// TODO: maybe?????? text input? idk.
				}

				public void keyPressed(KeyEvent e) {
					keysPressed.add(e.getKeyCode());
					keysHeld.add(e.getKeyCode());
				}

				public void keyReleased(KeyEvent e) {
					keysReleased.add(e.getKeyCode());
					keysHeld.remove(e.getKeyCode());
				}
			});

			this.addMouseListener(new MouseAdapter() {
				public void mousePressed(MouseEvent e) {
					if(SwingUtilities.isLeftMouseButton(e))        mouseButtons |= MOUSE_LBUTTON;
					else if(SwingUtilities.isRightMouseButton(e))  mouseButtons |= MOUSE_RBUTTON;
					else if(SwingUtilities.isMiddleMouseButton(e)) mouseButtons |= MOUSE_MBUTTON;
				}

				public void mouseReleased(MouseEvent e) {
					if(SwingUtilities.isLeftMouseButton(e))        mouseButtons &= ~MOUSE_LBUTTON;
					else if(SwingUtilities.isRightMouseButton(e))  mouseButtons &= ~MOUSE_RBUTTON;
					else if(SwingUtilities.isMiddleMouseButton(e)) mouseButtons &= ~MOUSE_MBUTTON;
				}

				public void mouseEntered(MouseEvent e) {
					mouseOver = true;
				}

				public void mouseExited(MouseEvent e) {
					mouseOver = false;
				}
			});

			this.addMouseMotionListener(new MouseMotionAdapter() {
				public void mouseMoved(MouseEvent e) {
					setMousePosition(e.getX() / cellSize, e.getY() / cellSize);
				}

				public void mouseDragged(MouseEvent e) {
					setMousePosition(e.getX() / cellSize, e.getY() / cellSize);
				}
			});

			this.addMouseWheelListener(new MouseWheelListener() {
				public void mouseWheelMoved(MouseWheelEvent e) {
					// apparently, at least on mac, *horizontal* scroll events are sent
					// with shift held down. nyokay. Seems like a hack.
					if(e.isShiftDown()) {
						if(e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
							mouseWheelX = e.getUnitsToScroll();
						} else {
							// "block" scrolling??? no idea when this happens.
							// let's do... something.
							mouseWheelX = e.getWheelRotation();
						}
					} else {
						if(e.getScrollType() == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
							mouseWheelY = e.getUnitsToScroll();
						} else {
							mouseWheelY = e.getWheelRotation();
						}
					}
				}
			});

			this.showCursor(false);
		}

		// ----------------------------------------------------------------------------------------
		// Base class method implementations

		@Override
		public void reset() {
			// ??????? do we really wanna do anything when they hit this button?
			// sure seems like it'd just fuck everything up if they hit it in
			// the middle of a program.
		}

		@Override
		public void paintDisplay(Graphics g) {
			synchronized(finalImageLock) {
				g.drawImage(finalImage, 0, 0, displayWidth, displayHeight, null);
			}

			if(DEBUG_OVERLAY) {
				g.setColor(Color.WHITE);
				g.setFont(bigFont);
				g.drawString(String.format("average frame length: %.2f ms",
					dbg_avgFrameLength / 1000000.0), 10, 30);

				// if(fbEnabled)
				// 	g.drawString("FB", 10, 60);

				// if(tmEnabled)
				// 	g.drawString("TM", 60, 60);

				// g.drawString(msPerFrame + " ms/frame", 10, 90);
			}
		}

		@Override
		public void writeToCtrl(int value) {
			int mode = value & MODE_MASK;

			// handle the undefined case by treating it like mode 1
			if(mode == 0)
				mode = MODE_FB_ENABLE;

			this.fbEnabled = (mode & MODE_FB_ENABLE) != 0;
			this.tmEnabled = (mode & MODE_TM_ENABLE) != 0;

			// extract ms/frame and clamp to valid range
			int msPerFrame = (value >> MS_PER_FRAME_SHIFT) & MS_PER_FRAME_MASK;
			msPerFrame = Math.min(msPerFrame, MAX_MS_PER_FRAME);
			msPerFrame = Math.max(msPerFrame, MIN_MS_PER_FRAME);

			this.msPerFrame = msPerFrame;
		}

		// big ugly thing to dispatch MMIO writes to their appropriate methods
		@Override
		public void handleWrite(int addr, int length, int value) {
			int page = (addr >> 12) & 0xF;

			switch(page) {
				// MMIO Page 0: global, tilemap control, input, and palette RAM
				case 0:
					if(addr >= DISPLAY_PALETTE_RAM) {
						this.writePalette(addr - DISPLAY_PALETTE_RAM, length, value);
					} else {
						// ignore non-word stores
						if(length != Memory.WORD_LENGTH_BYTES)
							break;

						switch(addr) {
							case DISPLAY_SYNC:          this.finishFrame();            break;
							case DISPLAY_RESET:         this.resetEverything();        break;

							// annoyingly, we are handling the write AFTER the RAM has already
							// been changed. so change it back.
							case DISPLAY_FRAME_COUNTER: this.updateFrameCounterRegister(); break;

							case DISPLAY_FB_CLEAR:      this.clearFb();                break;
							case DISPLAY_FB_IN_FRONT:   this.fbInFront = value != 0;   break;
							case DISPLAY_FB_PAL_OFFS:   this.setFbPalOffs(value);      break;
							case DISPLAY_FB_SCX:        this.setFbScx(value);          break;
							case DISPLAY_FB_SCY:        this.setFbScy(value);          break;
							case DISPLAY_TM_SCX:        this.setTmScx(value);          break;
							case DISPLAY_TM_SCY:        this.setTmScy(value);          break;
							case DISPLAY_KEY_HELD:      this.updateKeyHeld(value);     break;
							case DISPLAY_KEY_PRESSED:   this.updateKeyPressed(value);  break;
							case DISPLAY_KEY_RELEASED:  this.updateKeyReleased(value); break;
							case DISPLAY_MOUSE_VISIBLE: this.showCursor(value != 0);   break;
							default: break;
						}
					}
					break;

				// MMIO Pages 1-4: framebuffer data
				case 1: case 2: case 3: case 4:
					// 0xFFFF1000-0xFFFF4FFF: 128x128 (16,384) 1B pixels
					this.writeFb(addr - DISPLAY_FB_RAM, length, value);
					break;

				// MMIO Page 5: tilemap table and sprite table
				case 5:
					if(addr > DISPLAY_SPR_TABLE_END) {
						// 0xFFFF5C00-0xFFFF5FFF: nothing, right now. future expansion??
					} else if(addr >= DISPLAY_SPR_TABLE) {
						// 0xFFFF5800-0xFFFF5BFF: 256 4B sprite entries
						this.writeSprTable(addr - DISPLAY_SPR_TABLE, length, value);
					} else {
						// 0xFFFF5000-0xFFFF57FF: 32x32 2B tilemap entries
						this.writeTmTable(addr - DISPLAY_TM_TABLE, length, value);
					}
					break;

				// MMIO Pages 6-9: tilemap graphics
				case 6: case 7: case 8: case 9:
					this.writeTmGfx(addr - DISPLAY_TM_GFX, length, value);
					break;

				// MMIO Pages A-D: sprite graphics
				case 0xA: case 0xB: case 0xC: case 0xD:
					this.writeSprGfx(addr - DISPLAY_SPR_GFX, length, value);
					break;

				// MMIO Pages E-F: unused right now
				default:
					break;
			}
		}

		@Override
		public void handleRead(int addr, int length, int value) {
			if(addr == DISPLAY_SYNC) {
				this.waitForNextFrame();
			}
		}

		// ----------------------------------------------------------------------------------------
		// Reset

		private void resetEverything() {
			// we DON'T reset these, because they were just set by a write to DISPLAY_CTRL:
			// - msPerFrame
			// - fbEnabled
			// - tmEnabled
			// and mouseOver is its own little thing, not accessible by the user.

			// reset graphics registers
			this.initializePaletteRam();
			this.clearFb();
			this.clearTmRam();
			this.clearSprRam();
			frameCounter = 0;
			fbInFront = false;
			fbPalOffs = 0;
			fbScx = 0;
			fbScy = 0;
			tmScx = 0;
			tmScy = 0;
			lastFrameTime = System.nanoTime();

			// reset input stuff
			mouseButtons = 0;
			lastMouseButtons = 0;
			mouseWheelX = 0;
			mouseWheelY = 0;
			this.setMousePosition(-1, -1);
			this.updateMouseRegisters();

			this.showCursor(false);
			synchronized(Globals.memoryAndRegistersLock) {
				sim.writeWordToMemory(DISPLAY_MOUSE_VISIBLE, 0);
			}

			keysHeld.clear();

			if(DEBUG_OVERLAY) {
				dbg_avgFrameLength = 0;
			}
		}

		// ----------------------------------------------------------------------------------------
		// Input

		private void updateMouseRegisters() {
			synchronized(Globals.memoryAndRegistersLock) {
				sim.writeWordToMemory(DISPLAY_MOUSE_X,        mouseX);
				sim.writeWordToMemory(DISPLAY_MOUSE_Y,        mouseY);
				sim.writeWordToMemory(DISPLAY_MOUSE_HELD,     mouseButtons);
				sim.writeWordToMemory(DISPLAY_MOUSE_PRESSED,  mouseButtons & ~lastMouseButtons);
				sim.writeWordToMemory(DISPLAY_MOUSE_RELEASED, lastMouseButtons & ~mouseButtons);
				sim.writeWordToMemory(DISPLAY_MOUSE_WHEEL_X,  mouseWheelX);
				sim.writeWordToMemory(DISPLAY_MOUSE_WHEEL_Y,  mouseWheelY);
			}
		}

		private void setMousePosition(int x, int y) {
			// this check is here because if you click and drag on the window, the mouse
			// coords can actually go offscreen without triggering mouseExited.
			if(x < 0 || x >= N_COLUMNS || y < 0 || y >= N_ROWS) {
				mouseX = -1;
				mouseY = -1;
			} else {
				mouseX = x;
				mouseY = y;
			}
		}

		private void updateKeyHeld(int keyCode) {
			synchronized(Globals.memoryAndRegistersLock) {
				var held = keysHeld.contains(keyCode) ? 1 : 0;
				sim.writeWordToMemory(DISPLAY_KEY_HELD, held);
			}
		}

		private void updateKeyPressed(int keyCode) {
			synchronized(Globals.memoryAndRegistersLock) {
				var pressed = keysPressed.contains(keyCode) ? 1 : 0;
				sim.writeWordToMemory(DISPLAY_KEY_PRESSED, pressed);
			}
		}

		private void updateKeyReleased(int keyCode) {
			synchronized(Globals.memoryAndRegistersLock) {
				var released = keysReleased.contains(keyCode) ? 1 : 0;
				sim.writeWordToMemory(DISPLAY_KEY_RELEASED, released);
			}
		}

		private static Cursor blankCursor =
			Toolkit.getDefaultToolkit().createCustomCursor(
				new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB),
				new Point(0, 0),
				"blank cursor"
			);

		private void showCursor(boolean show) {
			if(show) {
				this.setCursor(Cursor.getDefaultCursor());
			} else {
				// apparently this is how you hide the cursor. by setting it to a
				// blank transparent image. lol
				this.setCursor(blankCursor);
			}
		}

		// ----------------------------------------------------------------------------------------
		// Frame synchronization

		private void finishFrame() {
			// Update graphics
			this.compositeFrame();

			// Update input
			this.updateMouseRegisters();
			lastMouseButtons = mouseButtons;

			// you never get events to tell you when the wheel STOPPED scrolling, so
			// we have to fake that.
			mouseWheelX = 0;
			mouseWheelY = 0;

			// Yep this still needs to be here.
			if(!mouseOver) {
				this.setMousePosition(-1, -1);
			}

			// we're talking n = 4 or 5 at the most, here.
			keysPressed.clear();
			keysReleased.clear();

			this.frameCounter++;
			this.updateFrameCounterRegister();
		}

		private void updateFrameCounterRegister() {
			synchronized(Globals.memoryAndRegistersLock) {
				sim.writeWordToMemory(DISPLAY_FRAME_COUNTER, this.frameCounter);
			}
		}

		private void waitForNextFrame() {
			long nsPerFrame = this.msPerFrame * 1000000L;
			long now = System.nanoTime();
			long processingTime = Math.max(0, now - this.lastFrameTime);
			long delay = nsPerFrame - Math.min(processingTime, nsPerFrame);

			if(delay > 0) {
				try {
					Thread.sleep(delay / 1000000, (int)(delay % 1000000));
				} catch(InterruptedException e) {}
			}

			now = System.nanoTime();

			if(DEBUG_OVERLAY) {
				if(this.lastFrameTime != 0) {
					long thisFrameLength = now - this.lastFrameTime;

					// the 10 here means something like "over the last 10 frames"
					dbg_avgFrameLength -= dbg_avgFrameLength / 10;
					dbg_avgFrameLength += thisFrameLength / 10;
				}
			}

			this.lastFrameTime = now;
		}

		// ----------------------------------------------------------------------------------------
		// Palette

		private static int[] Rgb222Intensities = { 0, 63, 127, 255 };

		// Initialize the palette RAM to a default palette, so you can start
		// drawing stuff right away without needing to do so from software.
		private void initializePaletteRam() {
			// default background color is black.
			paletteRam[0][0] = 0;
			paletteRam[1][0] = 0;
			paletteRam[2][0] = 0;

			// first 64 entries are the index, interpreted as RGB222.
			for(int i = 1; i < 64; i++) {
				paletteRam[0][i] = (byte)Rgb222Intensities[(i >> 4) & 3];
				paletteRam[1][i] = (byte)Rgb222Intensities[(i >> 2) & 3];
				paletteRam[2][i] = (byte)Rgb222Intensities[i & 3];
			}

			// next 16 entries are the classic display panel colors; so
			// you can convert classic colors to palette indexes by adding 64
			for(int i = 64; i < 80; i++) {
				var c = ClassicLEDDisplayPanel.PixelColors[i - 64];
				paletteRam[0][i] = (byte)c[0];
				paletteRam[1][i] = (byte)c[1];
				paletteRam[2][i] = (byte)c[2];
			}

			// rest of first half of palette is pure black
			for(int i = 80; i < 128; i++) {
				paletteRam[0][i] = 0;
				paletteRam[1][i] = 0;
				paletteRam[2][i] = 0;
			}

			// second half of palette is a smooth grayscale
			for(int i = 128; i < 256; i++) {
				int v = (i - 128) * 2;
				paletteRam[0][i] = (byte)v;
				paletteRam[1][i] = (byte)v;
				paletteRam[2][i] = (byte)v;
			}

			// finally, initialize the actual RAM to reflect the default palette
			synchronized(Globals.memoryAndRegistersLock) {
				for(int i = 0; i < 256; i++) {
					int color =
						((((int)paletteRam[0][i]) & 0xFF) << 16) |
						((((int)paletteRam[1][i]) & 0xFF) << 8) |
						(((int)paletteRam[2][i]) & 0xFF);

					sim.writeWordToMemory(DISPLAY_PALETTE_RAM + (i * 4), color);
				}
			}

			isPalDirty = true;
		}

		private void writePalette(int offs, int length, int value) {
			int entry = offs / 4;

			if(length == 4) {
				paletteRam[0][entry] = (byte)((value >>> 16) & 0xFF);
				paletteRam[1][entry] = (byte)((value >>> 8) & 0xFF);
				paletteRam[2][entry] = (byte)(value & 0xFF);
			} else if(length == 1) {
				// can't modify alpha
				if(offs < 3) {
					// offset 0 is blue, 1 is green, 2 is red
					paletteRam[2 - offs][entry] = (byte)(value & 0xFF);
				}
			} else if(offs == 0) {
				paletteRam[1][entry] = (byte)((value >>> 8) & 0xFF);
				paletteRam[2][entry] = (byte)(value & 0xFF);
			} else {
				paletteRam[0][entry] = (byte)(value & 0xFF);
			}

			// the BG color entry is not used for anything other than the BG
			// color layer, so we don't have to mark the palette dirty for that.
			if(entry != 0) {
				isPalDirty = true;
			}
		}

		private void buildPalette() {
			// if the palette changed, everything has to change.
			if(fbEnabled)
				isFbDirty = true;
			if(tmEnabled)
				isTmDirty = true;

			isSprDirty = true;

			isPalDirty = false;
		}

		// ----------------------------------------------------------------------------------------
		// Framebuffer

		private void clearFb() {
			Arrays.fill(fbRam, 0, fbRam.length, (byte)0);
			isFbDirty = true;
		}

		private void writeFb(int offs, int length, int value) {
			this.writeIntoByteArray(fbRam, offs, length, value);
			isFbDirty = true;
		}

		private void setFbPalOffs(int value) {
			this.fbPalOffs = value & 0xFF;
			isFbDirty = true;
		}

		private void setFbScx(int value) {
			this.fbScx = value & FB_SCX_MASK;
			isFbDirty = true;
		}

		private void setFbScy(int value) {
			this.fbScy = value & FB_SCY_MASK;
			isFbDirty = true;
		}

		private void buildFbLayer() {
			for(int y = 0; y < N_ROWS; y++) {
				for(int x = 0; x < N_COLUMNS; x++) {
					// yes, use the transparent color 0 if the index is 0;
					// the BG color will show through this image if so.
					// also have to do some Dumb Shit to zero extend the byte
					int colorIndex = fbRam[y*N_COLUMNS + x] & 0xFF;

					int px = (x + fbScx) & FB_SCX_MASK;
					int py = (y + fbScy) & FB_SCY_MASK;

					if(colorIndex == 0) {
						fbLayer.setSample(px, py, 0, 0);
					} else {
						// technically it'd be possible to have TWO transparent colors since
						// the palette index wraps around to 0, but whatever.
						fbLayer.setSample(px, py, 0, (colorIndex + fbPalOffs) & 0xFF);
					}
				}
			}

			isFbDirty = false;
		}

		// ----------------------------------------------------------------------------------------
		// Tilemap

		private void clearTmRam() {
			Arrays.fill(tmTable, 0, tmTable.length, (byte)0);
			Arrays.fill(tmGraphics, 0, tmGraphics.length, (byte)0);
			isTmDirty = true;
		}

		private void writeTmTable(int offs, int length, int value) {
			this.writeIntoByteArray(tmTable, offs, length, value);
			isTmDirty = true;
		}

		private void writeTmGfx(int offs, int length, int value) {
			this.writeIntoByteArray(tmGraphics, offs, length, value);
			isTmDirty = true;
		}

		private void setTmScx(int value) {
			this.tmScx = value & TM_SCX_MASK;
			isTmDirty = true;
		}

		private void setTmScy(int value) {
			this.tmScy = value & TM_SCY_MASK;
			isTmDirty = true;
		}

		private void buildTmLayers() {
			// First draw the FULL tilemap layers
			this.fillRaster(fullTmLayerHi, 0);
			this.fillRaster(fullTmLayerLo, 0);

			int entry = 0;

			for(int ty = 0; ty < N_TM_ROWS; ty++) {
				int py = ty * TILE_H;

				for(int tx = 0; tx < N_TM_COLUMNS; tx++) {
					int px = tx * TILE_W;

					// 1. get tile index and attributes
					int tileIndex = tmTable[entry] & 0xFF;
					int flags = tmTable[entry + 1] & 0xFF;
					var hflip = (flags & BIT_HFLIP) != 0;
					var vflip = (flags & BIT_VFLIP) != 0;
					var target = ((flags & BIT_PRIORITY) != 0) ? fullTmLayerHi : fullTmLayerLo;
					var palOffs = ((flags >> 4) & 0xF) * 16;

					// 2. get graphics
					int gfx = tileIndex * BYTES_PER_TILE;

					// 3. blit!
					this.blitTileOnto(target, px, py, tmGraphics, gfx, palOffs, hflip, vflip);

					// Next entry
					entry += TM_ENTRY_SIZE;
				}
			}

			// then extract JUST the visible portion based on scroll values.
			for(int py = 0; py < N_COLUMNS; py++) {
				for(int px = 0; px < N_ROWS; px++) {
					tmLayerLo.setSample(px, py, 0, fullTmLayerLo.getSample(
						(px + tmScx) & TM_SCX_MASK,
						(py + tmScy) & TM_SCY_MASK,
						0));

					tmLayerHi.setSample(px, py, 0, fullTmLayerHi.getSample(
						(px + tmScx) & TM_SCX_MASK,
						(py + tmScy) & TM_SCY_MASK,
						0));
				}
			}

			isTmDirty = false;
		}

		// ----------------------------------------------------------------------------------------
		// Sprite

		private void clearSprRam() {
			Arrays.fill(sprTable, 0, sprTable.length, (byte)0);
			Arrays.fill(sprGraphics, 0, sprGraphics.length, (byte)0);
			isSprDirty = true;
		}

		private void writeSprTable(int offs, int length, int value) {
			this.writeIntoByteArray(sprTable, offs, length, value);
			isSprDirty = true;
		}

		private void writeSprGfx(int offs, int length, int value) {
			this.writeIntoByteArray(sprGraphics, offs, length, value);
			isSprDirty = true;
		}

		private void buildSpriteLayer() {
			// Clear it out
			this.fillRaster(fullSpriteLayer, 0);

			// Draw all visible sprites with visible pixels to the full sprite layer
			int entry = sprTable.length - SPRITE_ENTRY_SIZE;

			for(int i = N_SPRITES - 1; i >= 0; i--) {
				int flags = sprTable[entry + 3] & 0xFF;

				// is sprite enabled?
				if((flags & BIT_ENABLE) != 0) {
					var isLarge = (flags & BIT_SIZE) != 0;

					// sign-extends, which is what we want
					int x = sprTable[entry];
					int y = sprTable[entry + 1];
					int x2 = x + (isLarge ? SPRITE_LARGE_W : TILE_W);
					int y2 = y + (isLarge ? SPRITE_LARGE_W : TILE_W);

					// does sprite overlap screen bounds?
					if(x < N_COLUMNS && y < N_COLUMNS && x2 > 0 && y2 > 0) {
						// get tile index and other attributes
						int tileIndex = sprTable[entry + 2] & 0xFF;
						var hflip = (flags & BIT_HFLIP) != 0;
						var vflip = (flags & BIT_VFLIP) != 0;
						var palOffs = ((flags >> 4) & 0xF) * 16;

						// get graphics
						int gfx = tileIndex * BYTES_PER_TILE;

						// draw the damn thing
						if(isLarge) {
							if(hflip) {
								if(vflip) {
									// flipped both ways
									this.blitLargeSpriteVH(x, y, gfx, palOffs);
								} else {
									// only horizontal flip
									this.blitLargeSpriteH(x, y, gfx, palOffs);
								}
							} else if(vflip) {
								// only vertical flip
								this.blitLargeSpriteV(x, y, gfx, palOffs);
							} else {
								// no flip
								this.blitLargeSprite(x, y, gfx, palOffs);
							}
						} else {
							this.blitTileOnto(fullSpriteLayer,
								x + SPRITE_LARGE_W, y + SPRITE_LARGE_H,
								sprGraphics, gfx, palOffs, hflip, vflip);
						}
					}
				}

				entry -= SPRITE_ENTRY_SIZE;
			}

			// Then extract JUST the visible portion
			for(int py = 0; py < N_COLUMNS; py++) {
				for(int px = 0; px < N_ROWS; px++) {
					spriteLayer.setSample(px, py, 0, fullSpriteLayer.getSample(
						px + SPRITE_LARGE_W,
						py + SPRITE_LARGE_H,
						0));
				}
			}

			isSprDirty = false;
		}

		// ----------------------------------------------------------------------------------------
		// Compositing

		private void compositeFrame() {
			// Update anything that is dirty
			if(isPalDirty)             { this.buildPalette(); }
			if(fbEnabled && isFbDirty) { this.buildFbLayer(); }
			if(tmEnabled && isTmDirty) { this.buildTmLayers(); }
			if(isSprDirty)             { this.buildSpriteLayer(); }

			// Build the final image in indexed color.
			// 1. Fill with background color
			this.fillRaster(finalRaster, 0);

			// 2. Draw layers on it
			if(!tmEnabled) {
				// only the framebuffer must be enabled.
				this.compositeOnto(finalRaster, fbLayer);
				this.compositeOnto(finalRaster, spriteLayer);
			} else {

				if(!fbEnabled) {
					this.compositeOnto(finalRaster, tmLayerLo);
					this.compositeOnto(finalRaster, spriteLayer);
					this.compositeOnto(finalRaster, tmLayerHi);
				} else if(fbInFront) {
					this.compositeOnto(finalRaster, tmLayerLo);
					this.compositeOnto(finalRaster, spriteLayer);
					this.compositeOnto(finalRaster, tmLayerHi);
					this.compositeOnto(finalRaster, fbLayer);
				} else {
					this.compositeOnto(finalRaster, fbLayer);
					this.compositeOnto(finalRaster, tmLayerLo);
					this.compositeOnto(finalRaster, spriteLayer);
					this.compositeOnto(finalRaster, tmLayerHi);
				}
			}

			// 3. Create the final image from the palette and composited raster
			// Frustratingly, there seems to be no way to reuse one BufferedImage/ColorModel
			// repeatedly. ColorModel is read-only, and must be recreated every time the
			// palette changes. BufferedImage's Raster can be changed, but not its ColorModel.
			// I guess it would be possible to cache the ColorModel and only update it when
			// the palette is dirty, but you still have to create the new BufferedImage every
			// frame. That seems okay, though, because BufferedImage doesn't copy the data.
			var pal = new IndexColorModel(8, 256, paletteRam[0], paletteRam[1], paletteRam[2]);
			synchronized(finalImageLock) {
				this.finalImage = new BufferedImage(pal, finalRaster, false, null);
				this.repaint();
			}
		}

		// ----------------------------------------------------------------------------------------
		// Helpers

		private void writeIntoByteArray(byte[] arr, int offs, int length, int value) {
			arr[offs] = (byte)(value & 0xFF);

			if(length > 1) {
				arr[offs + 1] = (byte)((value >>> 8) & 0xFF);

				if(length > 2) {
					arr[offs + 2] = (byte)((value >>> 16) & 0xFF);
					arr[offs + 3] = (byte)((value >>> 24) & 0xFF);
				}
			}
		}

		private void fillRaster(WritableRaster r, int index) {
			// I guess this is the only way to do this??? seems weird
			for(int y = 0; y < r.getHeight(); y++) {
				for(int x = 0; x < r.getWidth(); x++) {
					r.setSample(x, y, 0, index);
				}
			}
		}

		private void compositeOnto(WritableRaster dest, Raster src) {
			for(int y = 0; y < dest.getHeight(); y++) {
				for(int x = 0; x < dest.getWidth(); x++) {
					int index = src.getSample(x, y, 0);

					if(index != 0) {
						dest.setSample(x, y, 0, index);
					}
				}
			}
		}

		private void blitTileOnto(
			WritableRaster dest,
			int px,
			int py,
			byte[] gfx,
			int gfxOffset,
			int palOffset,
			boolean hflip,
			boolean vflip
		) {
			if(hflip) {
				if(vflip) {
					// Flip both ways
					// start on last column of last row
					gfxOffset += BYTES_PER_TILE - 1;

					for(int y = 0; y < TILE_H; y++) {
						for(int x = 0; x < TILE_W; x++) {
							int index = gfx[gfxOffset] & 0xFF;
							if(index != 0) {
								dest.setSample(px + x, py + y, 0, index + palOffset);
							}

							// get next pixel to the left
							gfxOffset--;
						}
					}

				} else {
					// Only flip horizontally
					// start on last column
					gfxOffset += TILE_W - 1;

					for(int y = 0; y < TILE_H; y++) {
						for(int x = 0; x < TILE_W; x++) {
							int index = gfx[gfxOffset] & 0xFF;
							if(index != 0) {
								dest.setSample(px + x, py + y, 0, index + palOffset);
							}

							// get next pixel to the left
							gfxOffset--;
						}

						// move ahead two rows, to go to next row
						gfxOffset += 2 * TILE_W;
					}
				}
			} else if(vflip) {
				// Only flip vertically

				// start on last row
				gfxOffset += BYTES_PER_TILE - TILE_W;

				for(int y = 0; y < TILE_H; y++) {
					for(int x = 0; x < TILE_W; x++) {
						int index = gfx[gfxOffset] & 0xFF;
						if(index != 0) {
							dest.setSample(px + x, py + y, 0, index + palOffset);
						}

						// get next pixel to the right
						gfxOffset++;
					}

					// move back two rows, to go to previous row
					gfxOffset -= 2 * TILE_W;
				}
			} else {
				// No flip
				for(int y = 0; y < TILE_H; y++) {
					for(int x = 0; x < TILE_W; x++) {
						int index = gfx[gfxOffset] & 0xFF;
						if(index != 0) {
							dest.setSample(px + x, py + y, 0, index + palOffset);
						}

						gfxOffset++;
					}
				}
			}
		}

		private void blitLargeSprite(int x, int y, int gfx, int palOffs) {
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W, y + SPRITE_LARGE_H,
				sprGraphics, gfx, palOffs, false, false);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W + TILE_W, y + SPRITE_LARGE_H,
				sprGraphics, gfx, palOffs, false, false);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W, y + SPRITE_LARGE_H + TILE_H,
				sprGraphics, gfx, palOffs, false, false);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W + TILE_W, y + SPRITE_LARGE_H + TILE_H,
				sprGraphics, gfx, palOffs, false, false);
		}

		private void blitLargeSpriteV(int x, int y, int gfx, int palOffs) {
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W, y + SPRITE_LARGE_H + TILE_H,
				sprGraphics, gfx, palOffs, false, true);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W + TILE_W, y + SPRITE_LARGE_H + TILE_H,
				sprGraphics, gfx, palOffs, false, true);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W, y + SPRITE_LARGE_H,
				sprGraphics, gfx, palOffs, false, true);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W + TILE_W, y + SPRITE_LARGE_H,
				sprGraphics, gfx, palOffs, false, true);
		}

		private void blitLargeSpriteH(int x, int y, int gfx, int palOffs) {
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W + TILE_W, y + SPRITE_LARGE_H,
				sprGraphics, gfx, palOffs, true, false);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W, y + SPRITE_LARGE_H,
				sprGraphics, gfx, palOffs, true, false);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W + TILE_W, y + SPRITE_LARGE_H + TILE_H,
				sprGraphics, gfx, palOffs, true, false);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W, y + SPRITE_LARGE_H + TILE_H,
				sprGraphics, gfx, palOffs, true, false);
		}

		private void blitLargeSpriteVH(int x, int y, int gfx, int palOffs) {
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W + TILE_W, y + SPRITE_LARGE_H + TILE_H,
				sprGraphics, gfx, palOffs, true, true);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W, y + SPRITE_LARGE_H + TILE_H,
				sprGraphics, gfx, palOffs, true, true);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W + TILE_W, y + SPRITE_LARGE_H,
				sprGraphics, gfx, palOffs, true, true);
			gfx += BYTES_PER_TILE;
			this.blitTileOnto(fullSpriteLayer,
				x + SPRITE_LARGE_W, y + SPRITE_LARGE_H,
				sprGraphics, gfx, palOffs, true, true);
		}
	}
}
