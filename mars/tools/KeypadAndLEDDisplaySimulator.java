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
	has no effect.

	Enhanced mode memory map
	========================

	MMIO Page  0: global, tilemap control, input, and palette RAM
	MMIO Pages 1-4: framebuffer data
	MMIO Page  5: tilemap table and sprite table
	MMIO Pages 6-9: tilemap graphics
	MMIO Pages A-D: sprite graphics
	MMIO Pages E-F: unused right now

	GLOBAL REGISTERS:

		0xFFFF0000: DISPLAY_CTRL.w           (WO)
			low 2 bits are mode:
				00: undefined
				01: framebuffer on
				10: tilemap on
				11: framebuffer and tilemap on
			bit 8 has no specific meaning but setting it along with mode switches to enhanced mode
			bits 16-23 are the milliseconds per frame used by DISPLAY_SYNC, but limited to
			the range [10, 100].

			bits 9-15 and 24-31 are undefined atm

			so set DISPLAY_CTRL to:
				(ms_per_frame << 16) | 0x100 | mode

		0xFFFF0004: DISPLAY_ORDER.w          (WO, order in which tilemap and framebuffer should
			be composited - 0 = tilemap in front of framebuffer, 0 = tilemap behind framebuffer)
		0xFFFF0008: DISPLAY_SYNC.w           (RW)
			write to indicate frame is over and ready for display (value is ignored)
			read to wait for next frame (always reads 0)
		0xFFFF000C: DISPLAY_FB_CLEAR.w       (WO, clears framebuffer to BG color when written)

	TILEMAP REGISTERS:

		0xFFFF0010: DISPLAY_TM_SCX.w         (WO, tilemap X scroll position)
		0xFFFF0014: DISPLAY_TM_SCY.w         (WO, tilemap Y scroll position)

		-- blank --

	INPUT REGISTERS:

		0xFFFF0020: DISPLAY_KEY_HELD.w       (write to choose key, read to get state)
		0xFFFF0024: DISPLAY_KEY_PRESSED.w    (write to choose key, read to get state)
		0xFFFF0028: DISPLAY_KEY_RELEASED.w   (write to choose key, read to get state)
		0xFFFF002C: DISPLAY_MOUSE_X.w        (RO, X position of mouse or -1 if mouse not over)
		0xFFFF0030: DISPLAY_MOUSE_Y.w        (RO, Y position of mouse or -1 if mouse not over)
		0xFFFF0034: DISPLAY_MOUSE_HELD.w     (RO, bitflags of mouse buttons held)
		0xFFFF0038: DISPLAY_MOUSE_PRESSED.w  (RO, bitflags of mouse buttons pressed, incl wheel)
		0xFFFF003C: DISPLAY_MOUSE_RELEASED.w (RO, bitflags of mouse buttons released)

		-- blank --

	PALETTE RAM:

		0xFFFF0C00-0xFFFF0FFF: 256 4B palette entries, byte order [BB, GG, RR, 00]
			(0x00RRGGBB in register becomes that in memory)

	FRAMEBUFFER DATA:

		0xFFFF1000-0xFFFF4FFF: 128x128 (16,384) 1B pixels, each is an index into the palette

	TILEMAP AND SPRITE TABLES:

		0xFFFF5000-0xFFFF57FF: 32x32 2B tilemap entries consisting of (tile, flags)
			tile graphics are fetched from (0xFFFF6000 + tile * 64)
			flags is xxxxHVP
				H = horizontal flip
				V = vertical flip
				P = priority (appears over sprites)

		0xFFFF5800-0xFFFF5FFF: 256 4B sprite entries consisting of (X, Y, tile, flags)
			X and Y are signed
			tile graphics are fetched from (0xFFFFA000 + tile * 64)
			flags is XXYYxHVE
				E = enable (1 for visible)
				H = horizontal flip
				V = vertical flip
				XX = X size (00 = 8px, 01 = 16px, 10 = 32px, 11 = 64px)
				YY = Y size (00 = 8px, 01 = 16px, 10 = 32px, 11 = 64px)
				if either size > 8px, tiles are put in order left-to-right, top-to-bottom, like
					1 2 3 4
					5 6 7 8
				or
					1 2
					3 4
					5 6
					7 8

	GRAPHICS DATA:
		0xFFFF6000-0xFFFF9FFF: 256 8x8 1Bpp indexed color tilemap tiles
		0xFFFFA000-0xFFFFDFFF: 256 8x8 1Bpp indexed color sprite tiles

	-- conspicuous blank space from 0xFFFFE000-0xFFFFFFFF that could be used for sound --

	Modes and how to switch
	=======================

		MODE 0: Classic mode

			It starts in mode 0, classic mode. This mode provides a 64x64-pixel linear
			framebuffer, 1 byte per pixel, with a fixed 16-color palette.

			A simple kind of double buffering is used to reduce the likelihood of tearing.
			The back buffer is readable and writable by the user and exists in memory
			in the address range [DISPLAY_BASE .. DISPLAY_END). The front buffer is
			*technically* writable by the user but well-behaved users would never do this.

			Writing 0 to DISPLAY_CTRL copies the back buffer into the front buffer.

			Writing a 1 to DISPLAY_CTRL copies the back buffer into the front buffer,
			then clears the back buffer to all 0 (black).

			Input is limited to the keyboard arrow keys and Z, X, C, B keys. Input is
			retrieved by reading from DISPLAY_KEYS, which returns the pressed state of
			each key as bitflags.

			As long as only the values 0 and 1 are written to DISPLAY_CTRL, it will stay
			in mode 0.

		MODE 1: Enhanced framebuffer

			Writing a value > 256 (0x100) to DISPLAY_CTRL will switch into enhanced mode.
			The mode number is the value written to (DISPLAY_CTRL & 3). If DISPLAY_CTRL & 3
			is 0 and the value is > 256, the results are undefined. Don't do that.

			All enhanced modes use a 128x128 display.

			Mode 1 is similar to mode 0, but with higher capabilities. This mode provides
			a linear framebuffer, 1 byte per pixel, with a user-definable 256-entry palette.

			Palette entries are RGB888, padded to 4 bytes. The palette is initialized in
			some way so that the palette index can be interpreted as RGB222 or RGB232 or
			something so that you can get to drawing stuff to the screen right away.

			The framebuffer is 128x128 pixels, the same size as the display. This already
			takes up 16KB of the tight 64KB of MMIO space, so that's all you get.

		MODE 2: Tilemap

			Mode 2 is totally different. In this mode, the tilemap is used instead.
			The tilemap is a 32x32-tile grid, where each tile is 8x8 pixels, for a total
			of 256x256 pixels (4 full screens).

			Each tile in the tilemap can be one of 256 tile graphics. There is enough
			space for all 256 tile indexes to have their own 8x8 1Bpp images.

			Each tile in the tilemap can also be flipped horizontally and/or vertically,
			or set to "priority" so that it appears in front of sprites. (Sprites are
			explained later.)

			The tilemap can be scrolled freely at pixel resolution on both axes. The scroll
			amount can be written as a signed integer but will be ANDed with 255. The tilemap
			wraps around at the edges.

		MODE 3: Both

			Mode 3 displays the framebuffer and the tilemap, with the tilemap in front of
			the framebuffer.

	Palette and Transparency
	========================

		There is a single global 256-entry palette. Each palette entry is 4 bytes, and is an
		RGB888 color with 1 byte unused. The tilemap and the sprites share the palette.

		Palette entry 0 is special as it specifies the background color. In tilemap and sprite
		graphics, a color index of 0 means "transparent," so this color will not appear in those
		graphics.

	Background Color
	================

		Palette entry 0 is the global background color to which the display is cleared before
		drawing anything else.

		If the framebuffer is visible, writing any value to DISPLAY_FB_CLEAR will fill the
		entire framebuffer with the background color.

		If the framebuffer is not visible but the tilemap is, the background color will appear
		behind transparent pixels in tiles.



	Sprites
	=======

		Sprites are available in any enhanced mode.

		There can be up to 256 sprites onscreen. Each sprite can be anywhere from 8x8 to 64x64
		pixels (1x1 to 8x8 tiles) in size, in powers of two, each dimension independently
		settable. Each sprite can be positioned anywhere onscreen including off all four sides.
		Each sprite can also be flipped horizontally or vertically.

		Sprite priority is by order in the list. Sprite 0 appears on top of sprite 1, which
		appears on top of sprite 2, etc.

		There are no "per-scanline limits" on the number of visible sprites.

		Sprite graphics are specified as 8x8 tiles just like the tilemap. For sprites bigger than
		8x8 pixels (1x1 tile), the tiles are assumed to be contiguous in memory, and are drawn
		in "reading order" (left-to-right, then top-to-bottom). So a 16x16 pixel sprite's tiles
		would be arranged like this on screen, where each number is the order they'd appear in
		memory:
			1 2
			3 4

	Graphics Data
	=============

		The tilemap and the sprites each have their own independent graphics data areas.

		The graphics are 8x8-pixel tiles, where each pixel is 1 byte, for a total of 64 bytes
		per tile. The pixels are stored in "reading order". Each pixel is an index into the
		global palette.

	Screen Compositing
	==================

		If DISPLAY_ORDER is 0 (the default), the display elements are drawn from back (first drawn)
		to front (last drawn) like so:

			- Background color
			- Framebuffer
			- Tilemap tiles without priority
			- Sprites, from sprite 255 down to sprite 0
			- Tilemap tiles with priority

		If DISPLAY_ORDER is set to 1, the framebuffer is instead drawn last, on top of
		everything else.

	*/

	// --------------------------------------------------------------------------------------------
	// Constants

	static final long serialVersionUID = 1; // To eliminate a warning about serializability.

	private static final String version = "Version 2";
	private static final String title = "Keypad and LED Display MMIO Simulator";
	private static final String heading = "Classic Mode";
	private static final String enhancedHeading = "Enhanced Mode";
	private static final int DISPLAY_CTRL = Memory.memoryMapBaseAddress;
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
		});
		subPanel.add(gridCheckBox);

		zoomCheckBox = new JCheckBox("Zoom");
		zoomCheckBox.addItemListener((e) -> {
			displayPanel.setZoomed(e.getStateChange() == ItemEvent.SELECTED);
			displayPanel.revalidate();
			this.theWindow.pack();
			displayPanel.repaint();
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
		updateDisplay();
	}

	/** Used to watch for writes to control registers. */
	@Override
	protected void processMIPSUpdate(Observable memory, AccessNotice accessNotice) {
		MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;

		if(notice.getAccessType() == AccessNotice.WRITE) {
			// Can't switch on addresses because they're dynamic based on
			// Memory.memoryMapBaseAddress.
			if(notice.getAddress() == DISPLAY_CTRL) {
				int value = notice.getValue();

				if(value >= ENHANCED_MODE_SWITCH_VALUE) {
					this.switchToEnhancedMode();
				}

				this.displayPanel.writeToCtrl(value);
			}
		} else {
			// reads...
		}
	}

	/** Called any time an MMIO access is made. */
	@Override
	protected void updateDisplay() {
		displayPanel.repaintIfNeeded();
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
					requestFocusInWindow();
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

		private static final int DISPLAY_KEYS = DISPLAY_CTRL + Memory.WORD_LENGTH_BYTES;
		private static final int DISPLAY_BASE = DISPLAY_KEYS + Memory.WORD_LENGTH_BYTES;
		private static final int DISPLAY_SIZE = N_ROWS * N_COLUMNS; // bytes
		private static final int DISPLAY_END = DISPLAY_BASE + DISPLAY_SIZE;

		private static final int COLOR_MASK = 15;

		/** color palette. */
		private static final int[][] PixelColors = new int[][] {
			new int[]{0, 0, 0},       // black
			new int[]{255, 0, 0},     // red
			new int[]{255, 127, 0},   // orange
			new int[]{255, 255, 0},   // yellow
			new int[]{0, 255, 0},     // green
			new int[]{51, 102, 255},  // blue
			new int[]{255, 0, 255},   // magenta
			new int[]{255, 255, 255}, // white

			// extended colors!
			new int[]{63, 63, 63},    // dark grey
			new int[]{127, 0, 0},     // brick
			new int[]{127, 63, 0},    // brown
			new int[]{192, 142, 91},  // tan
			new int[]{0, 127, 0},     // dark green
			new int[]{25, 50, 127},   // dark blue
			new int[]{63, 0, 127},    // purple
			new int[]{127, 127, 127}, // light grey
		};

		private int keyState = 0;
		private BufferedImage image =
			new BufferedImage(N_COLUMNS, N_ROWS, BufferedImage.TYPE_INT_RGB);

		public ClassicLEDDisplayPanel(KeypadAndLEDDisplaySimulator sim) {
			super(sim, N_COLUMNS, N_ROWS, CELL_DEFAULT_SIZE, CELL_ZOOMED_SIZE);

			this.addKeyListener(new KeyListener() {
				public void keyTyped(KeyEvent e) {
				}

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

			if(!sim.isBeingUsedAsAMarsTool || sim.connectButton.isConnected()) {
				synchronized(Globals.memoryAndRegistersLock) {
					// 1 is (DISPLAY_KEYS - MMIO Base) / 4
					Globals.memory.getMMIOPage(0)[1] = newState;
				}
			}
		}

		/** quickly clears the graphics memory to 0 (black). */
		@Override
		public void reset() {
			this.resetGraphicsMemory(true);
			this.setShouldRepaint(true);
		}

		@Override
		public void writeToCtrl(int value) {
			// Copy values from memory to internal buffer, reset if we must.
			this.updateImage();

			if(value != 0)
				this.resetGraphicsMemory(false);

			this.setShouldRepaint(true);
		}

		private void resetGraphicsMemory(boolean clearBuffer) {
			synchronized(Globals.memoryAndRegistersLock) {
				// I hate using magic values like this but: these are the addresses of
				// DISPLAY_BASE and DISPLAY_END, minus the MMIO base address, divided
				// by 4 since the array indexes are words, not bytes.
				Arrays.fill(Globals.memory.getMMIOPage(0), 2, 1024, 0);
				Arrays.fill(Globals.memory.getMMIOPage(1), 0,    2, 0);

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
							if(ptr == 1024) {
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
		}
	}

	// --------------------------------------------------------------------------------------------
	// Enhanced display

	/** The new, enhanced display. */
	private static class EnhancedLEDDisplayPanel extends LEDDisplayPanel {
		// Display and framebuffer size
		private static final int N_COLUMNS = 128;
		private static final int N_ROWS = 128;
		private static final int CELL_DEFAULT_SIZE = 4;
		private static final int CELL_ZOOMED_SIZE = 6;

		// Tilemap constants
		private static final int N_TM_COLUMNS = 32;
		private static final int N_TM_ROWS = 32;

		// Tilemap/sprite flag constants
		private static final int PRIORITY = 1; // for tiles
		private static final int ENABLE = 1; // for sprites
		private static final int VFLIP = 2; // tiles + sprites
		private static final int HFLIP = 4; // tiles + sprites

		// DISPLAY_CTRL
		private int msPerFrame = 16;
		private boolean fbEnabled = true;
		private boolean tmEnabled = false;

		// DISPLAY_ORDER
		private boolean fbInFront = false;

		// DISPLAY_TM_SCX/SCY
		private int tmScx = 0;
		private int tmScy = 0;

		// Palette RAM
		private byte[][] paletteRam = new byte[256][4];

		// Framebuffer RAM
		private byte[] fbRam = new byte[N_COLUMNS * N_ROWS];

		// Tilemap table and graphics
		private byte[] tmTable = new byte[N_TM_COLUMNS * N_TM_ROWS * 2];
		private byte[] tmGraphics = new byte[256 * 8 * 8];

		// Sprite table and graphics
		private byte[] sprTable = new byte[256 * 4];
		private byte[] sprGraphics = new byte[256 * 8 * 8];

		// ----------------------------------------------------------------------------------------
		// Constructor

		public EnhancedLEDDisplayPanel(KeypadAndLEDDisplaySimulator sim) {
			super(sim, N_COLUMNS, N_ROWS, CELL_DEFAULT_SIZE, CELL_ZOOMED_SIZE);
		}

		// ----------------------------------------------------------------------------------------
		// Base class method implementations

		@Override
		public void reset() {
			// ??????
		}

		@Override
		public void writeToCtrl(int value) {
			// TODO
		}

		@Override
		public void paintDisplay(Graphics g) {
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, displayWidth, displayHeight);
			g.setColor(Color.RED);
			g.setFont(new Font("Sans-Serif", Font.BOLD, 24));
			g.drawString("~Enhanced~", 10, displayHeight - 10);
		}
	}
}
