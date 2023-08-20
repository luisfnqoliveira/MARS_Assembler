package mars.tools;

import javax.swing.*;
import javax.swing.plaf.basic.*;
import java.awt.*;
import java.awt.event.*;
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

		0xFFFF0000: DISPLAY_CTRL.w (write-only)
		0xFFFF0004: DISPLAY_KEYS.w (read-only)
		0xFFFF0008: DISPLAY_BASE.b (write-only) - start of user-written LED area
		0xFFFF1007: DISPLAY_END.b - 1 (write-only) - end of user-written LED area

		-- nothing --

		0xFFFF1008: DISPLAY_BUFFER_START.b - start of internal buffer
		0xFFFF2007: DISPLAY_BUFFER_END.b - 1 - end of internal buffer
	*/

	// --------------------------------------------------------------------------------------------
	// Constants

	static final long serialVersionUID = 1; // To eliminate a warning about serializability.

	private static final String version = "Version 2";
	private static final String title = "Keypad and LED Display MMIO Simulator";
	private static final String heading = "";
	private static final int N_COLUMNS = 64;
	private static final int N_ROWS = 64;
	private static final int DISPLAY_CTRL = Memory.memoryMapBaseAddress;
	private static final int DISPLAY_KEYS = DISPLAY_CTRL + Memory.WORD_LENGTH_BYTES;
	private static final int DISPLAY_BASE = DISPLAY_KEYS + Memory.WORD_LENGTH_BYTES;
	private static final int DISPLAY_SIZE = N_ROWS * N_COLUMNS; // bytes
	private static final int DISPLAY_END = DISPLAY_BASE + DISPLAY_SIZE;

	// the 4096 is there to give a "buffer zone" between the user-written area and the
	// display buffer. this way, writes past the end of the display will be invisible.
	// ...it's just to give the students a little leeway. :P
	private static final int DISPLAY_BUFFER_START = DISPLAY_END + 4096;
	private static final int DISPLAY_BUFFER_END = DISPLAY_BUFFER_START + DISPLAY_SIZE;

	private static final int KEY_U = 1;
	private static final int KEY_D = 2;
	private static final int KEY_L = 4;
	private static final int KEY_R = 8;
	private static final int KEY_B = 16;
	private static final int KEY_Z = 32;
	private static final int KEY_X = 64;
	private static final int KEY_C = 128;

	// --------------------------------------------------------------------------------------------
	// Instance fields

	private JPanel panel;
	private LEDDisplayPanel displayPanel;
	private boolean shouldRedraw = true;

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

		displayPanel = new ClassicLEDDisplayPanel();

		JPanel subPanel = new JPanel();
		JCheckBox gridCheckBox = new JCheckBox("Show Grid Lines");
		gridCheckBox.addItemListener((e) -> {
			displayPanel.setGridLinesEnabled(e.getStateChange() == ItemEvent.SELECTED);
			displayPanel.revalidate();
			this.theWindow.pack();
			displayPanel.repaint();
		});
		subPanel.add(gridCheckBox);

		JCheckBox zoomCheckBox = new JCheckBox("Zoom");
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
	}

	/** Called when the Connect button is clicked, to hook it into the memory subsystem. */
	@Override
	protected void addAsObserver() {
		addAsObserver(DISPLAY_CTRL, DISPLAY_KEYS);
	}

	/** Called when the Reset button is clicked. */
	@Override
	protected void reset() {
		displayPanel.resetGraphicsMemory();
		shouldRedraw = true;
		updateDisplay();
	}

	/** Used to watch for writes to control registers. */
	@Override
	protected void processMIPSUpdate(Observable memory, AccessNotice accessNotice) {
		MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;

		// Only care about writes.
		if(notice.getAccessType() != AccessNotice.WRITE)
			return;

		// Can't actually switch on addresses because they're dynamic based on
		// Memory.memoryMapBaseAddress.
		if(notice.getAddress() == DISPLAY_CTRL) {
			displayPanel.setShouldClear(notice.getValue() != 0);
			shouldRedraw = true;

			// Copy values from memory to internal buffer, reset if we must.
			try {
				synchronized(Globals.memoryAndRegistersLock) {
					// Ensure block for destination exists
					Globals.memory.setRawWord(DISPLAY_BUFFER_START, 0x0);
					Globals.memory.setRawWord(DISPLAY_BUFFER_END - 0x4, 0x0);
					Globals.memory.copyMMIOFast(DISPLAY_BASE, DISPLAY_BUFFER_START,
						DISPLAY_SIZE);
				}
			}
			catch(AddressErrorException aee) {
				System.out.println("Tool author specified incorrect MMIO address!" + aee);
				System.exit(0);
			}

			displayPanel.clearIfNeeded();
		}
	}

	/** Called any time an MMIO access is made. */
	@Override
	protected void updateDisplay() {
		if(shouldRedraw) {
			shouldRedraw = false;
			displayPanel.repaint();
		}
	}

	// --------------------------------------------------------------------------------------------
	// Common base class for both kinds of displays

	private abstract class LEDDisplayPanel extends JPanel {
		private boolean haveFocus = false;

		public abstract void setGridLinesEnabled(boolean e);
		public abstract void setZoomed(boolean e);
		public abstract void setShouldClear(boolean c);
		public abstract void clearIfNeeded();
		public abstract void resetGraphicsMemory();

		public LEDDisplayPanel() {
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
				}

				public void focusLost(FocusEvent e) {
					haveFocus = false;
				}
			});
		}
	}

	// --------------------------------------------------------------------------------------------
	// Classic display

	/** color palette. has to be out here for some Java reason */
	private static final Color[] ClassicPixelColors = new Color[] {
		new Color(0, 0, 0),       // black
		new Color(255, 0, 0),     // red
		new Color(255, 127, 0),   // orange
		new Color(255, 255, 0),   // yellow
		new Color(0, 255, 0),     // green
		new Color(51, 102, 255),  // blue
		new Color(255, 0, 255),   // magenta
		new Color(255, 255, 255), // white

		// extended colors!
		new Color(63, 63, 63),    // dark grey
		new Color(127, 0, 0),     // brick
		new Color(127, 63, 0),    // brown
		new Color(192, 142, 91),  // tan
		new Color(0, 127, 0),     // dark green
		new Color(25, 50, 127),   // dark blue
		new Color(63, 0, 127),    // purple
		new Color(127, 127, 127), // light grey
	};

	/** CLASSIC: the actual graphical display. */
	private class ClassicLEDDisplayPanel extends LEDDisplayPanel {
		private static final int CELL_DEFAULT_SIZE = 8;
		private static final int CELL_ZOOMED_SIZE = 12;
		private static final int COLOR_MASK = 15;

		private int cellSize = CELL_DEFAULT_SIZE;
		private int cellPadding = 0;
		private int pixelSize;
		private int displayWidth;
		private int displayHeight;

		private boolean shouldClear = false;
		private boolean drawGridLines = false;
		private boolean zoomed = false;

		private int keyState;

		public ClassicLEDDisplayPanel() {
			super();

			this.recalcSizes();

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

		private void recalcSizes() {
			cellSize      = cellSize = (zoomed ? CELL_ZOOMED_SIZE : CELL_DEFAULT_SIZE);
			cellPadding   = drawGridLines ? 1 : 0;
			pixelSize     = cellSize - cellPadding;
			pixelSize     = cellSize - cellPadding;
			displayWidth  = (N_COLUMNS * cellSize);
			displayHeight = (N_ROWS * cellSize);
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

		public void setShouldClear(boolean c) {
			this.shouldClear = c;
		}

		public void clearIfNeeded() {
			if(this.shouldClear) {
				this.shouldClear = false;
				this.resetGraphicsMemory();
			}
		}

		/** set the key state to the new state, and update the value in MIPS memory
		for the program to be able to read. */
		private void changeKeyState(int newState) {
			keyState = newState;

			if(!isBeingUsedAsAMarsTool || connectButton.isConnected()) {
				try {
					synchronized(Globals.memoryAndRegistersLock) {
						Globals.memory.setRawWord(DISPLAY_KEYS, newState);
					}
				}
				catch(AddressErrorException aee) {
					System.out.println("Tool author specified incorrect MMIO address!" + aee);
					System.exit(0);
				}
			}
		}

		/** quickly clears the graphics memory to 0 (black). */
		public void resetGraphicsMemory() {
			try {
				synchronized(Globals.memoryAndRegistersLock) {
					Globals.memory.zeroMMIOFast(DISPLAY_BASE, DISPLAY_SIZE);
				}
			}
			catch(AddressErrorException aee) {
				System.out.println("Tool author specified incorrect MMIO address!" + aee);
				System.exit(0);
			}
		}

		public void paintComponent(Graphics g) {
			if(!connectButton.isConnected()) {
				g.setColor(Color.BLACK);
				g.fillRect(0, 0, displayWidth, displayHeight);
				g.setColor(Color.RED);
				g.setFont(new Font("Sans-Serif", Font.BOLD, 24));
				g.drawString("vvvv CLICK THE CONNECT BUTTON!", 10, displayHeight - 10);
				return;
			}

			if(drawGridLines) {
				g.setColor(Color.GRAY);
				g.fillRect(0, 0, displayWidth, displayHeight);
			}

			int ptr = DISPLAY_BUFFER_START;

			try {
				synchronized(Globals.memoryAndRegistersLock) {
					for(int row = 0; row < N_ROWS; row++) {
						int y = row * cellSize;

						for(int col = 0, x = 0; col < N_COLUMNS; col += 4, ptr += 4) {
							int pixel = Globals.memory.getWordNoNotify(ptr);

							g.setColor(ClassicPixelColors[pixel & COLOR_MASK]);
							g.fillRect(x, y, pixelSize, pixelSize);
							x += cellSize;
							g.setColor(ClassicPixelColors[(pixel >> 8) & COLOR_MASK]);
							g.fillRect(x, y, pixelSize, pixelSize);
							x += cellSize;
							g.setColor(ClassicPixelColors[(pixel >> 16) & COLOR_MASK]);
							g.fillRect(x, y, pixelSize, pixelSize);
							x += cellSize;
							g.setColor(ClassicPixelColors[(pixel >> 24) & COLOR_MASK]);
							g.fillRect(x, y, pixelSize, pixelSize);
							x += cellSize;
						}
					}
				}
			}
			catch(AddressErrorException aee) {
				System.out.println("Tool author specified incorrect MMIO address!" + aee);
				System.exit(0);
			}

			// TODO: if we don't have focus, draw an overlay saying to click on the display
		}
	}
}
