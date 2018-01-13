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
 Copyright (c) 2009 Jose Baiocchi

 Developed by Jose Baiocchi (baiocchi@cs.pitt.edu)

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
 * @version 1.1. 16 February 2010.
 */
public class KeypadAndLEDDisplaySimulator extends AbstractMarsToolAndApplication {

	static final long serialVersionUID = 1; /* To eliminate a warning about serializability. */


	private static String version = "Version 1.0 64x64";
	private static String heading = "Keypad and LED Display MMIO Simulator";
	private static final int PIXEL_BITS = 8;
	private static final int PIXELS_PER_BYTE = 8 / PIXEL_BITS;
	private static final int PIXEL_MASK = 0xFF;
	private static final int N_COLUMNS = 64;
	private static final int N_ROWS = 64;
	private static final int RECEIVER_CONTROL = Memory.memoryMapBaseAddress;
	private static final int RECEIVER_DATA = RECEIVER_CONTROL + Memory.WORD_LENGTH_BYTES;
	private static final int LED_START = RECEIVER_DATA + Memory.WORD_LENGTH_BYTES;
	private static final int LED_END = LED_START + N_ROWS * (N_COLUMNS / PIXELS_PER_BYTE);
	private static final int CELL_WIDTH = 8;
	private static final int CELL_HEIGHT = 8;
	private static final int CELL_PADDING = 0;
	private static final int PIXEL_WIDTH = CELL_WIDTH - 1 * CELL_PADDING;
	private static final int PIXEL_HEIGHT = CELL_HEIGHT - 1 * CELL_PADDING;
	private static final int DISPLAY_WIDTH = (N_COLUMNS * CELL_WIDTH) + (2 * CELL_PADDING);
	private static final int DISPLAY_HEIGHT = (N_ROWS * CELL_HEIGHT) + (2 * CELL_PADDING);
	private GraphicsMemory ledMemory;
	private JPanel keypadAndDisplayArea;
	private JPanel keypadPanel;
	private JPanel displayPanel;

	/**
	 * Simple constructor, licenseely used to run a stand-alone keyboard/display
	 * simulator.
	 *
	 * @param title
	 *            String containing title for title bar
	 * @param heading
	 *            String containing text for heading shown in upper part of
	 *            window.
	 */
	public KeypadAndLEDDisplaySimulator(String title, String heading) {
		super(title, heading);
	}

	/**
	 * Simple constructor, likely used by the MARS Tools menu mechanism
	 */
	public KeypadAndLEDDisplaySimulator() {
		super(heading + ", " + version, heading);
	}

	/**
	 * Main provided for pure stand-alone use. Recommended stand-alone use is to
	 * write a driver program that instantiates a KeyboardAndDisplaySimulator
	 * object then invokes its go() method. "stand-alone" means it is not
	 * invoked from the MARS Tools menu. "Pure" means there is no driver program
	 * to invoke the application.
	 *
	 * @param args
	 *            String array containing command line arguments (ignored)
	 */
	public static void main(String[] args) {
		new KeypadAndLEDDisplaySimulator(heading + " stand-alone, " + version,
				heading).go();
	}

	/**
	 * Required MarsTool method to return Tool name.
	 *
	 * @return Tool name. MARS will display this in menu item.
	 */
	public String getName() {
		return "Keypad and LED Display Simulator";
	}

	/**
	 * Override the inherited method, which registers us as an Observer over the
	 * Memory Mapped I/O segment (address range VGA_START:VGA_END). If you use
	 * the inherited GUI buttons, this method is invoked when you click
	 * "Connect" button on MarsTool or the "Assemble and Run" button on a
	 * Mars-based app.
	 */
	protected void addAsObserver() {
		// We observe MIPS writes to LED_START:LED_END to update the display.
		addAsObserver(LED_START, LED_END);
		// We also observe MIPS reads from RECEIVER_DATA
		addAsObserver(RECEIVER_DATA, RECEIVER_DATA);
	}

	/**
	 * Method that constructs the main display area.
	 *
	 * @return the GUI component
	 */
	protected JComponent buildMainDisplayArea() {
		ledMemory = new GraphicsMemory(LED_END - LED_START
				+ Memory.WORD_LENGTH_BYTES);
		keypadAndDisplayArea = new JPanel();
		displayPanel = new LEDDisplayPanel();
		displayPanel.setPreferredSize(new Dimension(DISPLAY_WIDTH, DISPLAY_HEIGHT));
		keypadPanel = new KeypadPanel();

		keypadAndDisplayArea.add(keypadPanel);
		keypadAndDisplayArea.add(displayPanel);

		// keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("typed a"),"RespondToLeftKey");
		// keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("typed d"),"RespondToRightKey");
		// keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("typed w"),"RespondToUpKey");
		// keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("typed s"),"RespondToDownKey");
		// keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("typed b"),"RespondToActionKey");

		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, false),"LeftKeyPressed");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false),"RightKeyPressed");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, false),"UpKeyPressed");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, false),"DownKeyPressed");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, false),"ActionKeyPressed");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0, true), "LeftKeyReleased");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true), "RightKeyReleased");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0, true), "UpKeyReleased");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0, true), "DownKeyReleased");
		keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_B, 0, true), "ActionKeyReleased");

		class LeftAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).pressKey(KeyEvent.VK_KP_LEFT); }
		}
		class RightAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).pressKey(KeyEvent.VK_KP_RIGHT); }
		}
		class UpAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).pressKey(KeyEvent.VK_KP_UP); }
		}
		class DownAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).pressKey(KeyEvent.VK_KP_DOWN); }
		}
		class ActionAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).pressKey(KeyEvent.VK_B); }
		}
		class RLeftAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).releaseKey(KeyEvent.VK_KP_LEFT); }
		}
		class RRightAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).releaseKey(KeyEvent.VK_KP_RIGHT); }
		}
		class RUpAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).releaseKey(KeyEvent.VK_KP_UP); }
		}
		class RDownAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).releaseKey(KeyEvent.VK_KP_DOWN); }
		}
		class RActionAction extends AbstractAction {
			public void actionPerformed(ActionEvent e) {((KeypadPanel)keypadPanel).releaseKey(KeyEvent.VK_B); }
		}

		/* This is so hacky and verbose. I don't know of a better way. */
		// JB: I feel you, Jose. "Verbose" is the best adjective for Java.
		keypadAndDisplayArea.getActionMap().put("LeftKeyPressed", new LeftAction());
		keypadAndDisplayArea.getActionMap().put("RightKeyPressed", new RightAction());
		keypadAndDisplayArea.getActionMap().put("UpKeyPressed", new UpAction());
		keypadAndDisplayArea.getActionMap().put("DownKeyPressed", new DownAction());
		keypadAndDisplayArea.getActionMap().put("ActionKeyPressed", new ActionAction());
		keypadAndDisplayArea.getActionMap().put("LeftKeyReleased", new RLeftAction());
		keypadAndDisplayArea.getActionMap().put("RightKeyReleased", new RRightAction());
		keypadAndDisplayArea.getActionMap().put("UpKeyReleased", new RUpAction());
		keypadAndDisplayArea.getActionMap().put("DownKeyReleased", new RDownAction());
		keypadAndDisplayArea.getActionMap().put("ActionKeyReleased", new RActionAction());

		return keypadAndDisplayArea;
	}

	/**
	 * Update the MMIO Control register memory cell. We will delegate.
	 */
	private void updateMMIOControl(int addr, int intValue) {
		updateMMIOControlAndData(addr, intValue, 0, 0, true);
	}

	/**
	 * Update the MMIO Control and Data register pair -- 2 memory cells. We will
	 * delegate.
	 */
	private void updateMMIOControlAndData(int controlAddr, int controlValue,
			int dataAddr, int dataValue) {
		updateMMIOControlAndData(controlAddr, controlValue, dataAddr,
				dataValue, false);
	}

	/**
	 * This one does the work: update the MMIO Control and optionally the Data
	 * register as well NOTE: last argument TRUE means update only the MMIO
	 * Control register; FALSE means update both Control and Data.
	 */
	private synchronized void updateMMIOControlAndData(int controlAddr,
			int controlValue, int dataAddr, int dataValue, boolean controlOnly) {
		if (!this.isBeingUsedAsAMarsTool
				|| (this.isBeingUsedAsAMarsTool && connectButton.isConnected())) {
			synchronized (Globals.memoryAndRegistersLock) {
				try {
					Globals.memory.setRawWord(controlAddr, controlValue);
					if (!controlOnly)
						Globals.memory.setRawWord(dataAddr, dataValue);
				} catch (AddressErrorException aee) {
					System.out
							.println("Tool author specified incorrect MMIO address!"
									+ aee);
					System.exit(0);
				}
			}
			// HERE'S A HACK!! Want to immediately display the updated memory
			// value in MARS
			// but that code was not written for event-driven update (e.g.
			// Observer) --
			// it was written to poll the memory cells for their values. So we
			// force it to do so.
			if (Globals.getGui() != null
					&& Globals.getGui().getMainPane().getExecutePane()
							.getTextSegmentWindow().getCodeHighlighting()) {
				Globals.getGui().getMainPane().getExecutePane()
						.getDataSegmentWindow().updateValues();
			}
		}
	}

	/**
	 * Return value of the given MMIO control register after ready (low order)
	 * bit set (to 1). Have to preserve the value of Interrupt Enable bit (bit
	 * 1).
	 */
	private static boolean isReadyBitSet(int mmioControlRegister) {
		try {
			return (Globals.memory.get(mmioControlRegister,
					Memory.WORD_LENGTH_BYTES) & 1) == 1;
		} catch (AddressErrorException aee) {
			System.out.println("Tool author specified incorrect MMIO address!"
					+ aee);
			System.exit(0);
		}
		return false; // to satisfy the compiler -- this will never happen.
	}

	/**
	 * Return value of the given MMIO control register after ready (low order)
	 * bit set (to 1). Have to preserve the value of Interrupt Enable bit (bit
	 * 1).
	 */
	private static int readyBitSet(int mmioControlRegister) {
		try {
			return Globals.memory.get(mmioControlRegister,
					Memory.WORD_LENGTH_BYTES) | 1;
		} catch (AddressErrorException aee) {
			System.out.println("Tool author specified incorrect MMIO address!"
					+ aee);
			System.exit(0);
		}
		return 1; // to satisfy the compiler -- this will never happen.
	}

	/**
	 * Return value of the given MMIO control register after ready (low order)
	 * bit cleared (to 0). Have to preserve the value of Interrupt Enable bit
	 * (bit 1). Bits 2 and higher don't matter.
	 */
	private static int readyBitCleared(int mmioControlRegister) {
		try {
			return Globals.memory.get(mmioControlRegister,
					Memory.WORD_LENGTH_BYTES) & 2;
		} catch (AddressErrorException aee) {
			System.out.println("Tool author specified incorrect MMIO address!"
					+ aee);
			System.exit(0);
		}
		return 0; // to satisfy the compiler -- this will never happen.
	}

	/**
	 * Update display when connected MIPS program accesses LED display address
	 * range.
	 *
	 * @param memory
	 *            int offset = column % PIXELS_PER_BYTE;
	 *
	 *            the attached memory
	 * @param accessNotice
	 *            information provided by memory in MemoryAccessNotice object
	 */
	protected void processMIPSUpdate(Observable memory,
			AccessNotice accessNotice) {
		MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;
		// If MIPS program has just read (loaded) the receiver (keypad) data
		// register,
		// then clear the Ready bit to indicate there is no longer a keystroke
		// available.
		// If Ready bit was initially clear, they'll get the old keystroke --
		// serves 'em right
		// for not checking!
		if (notice.getAccessType() == AccessNotice.READ) {
			if (notice.getAddress() == RECEIVER_DATA) {
				updateMMIOControl(RECEIVER_CONTROL,
						readyBitCleared(RECEIVER_CONTROL));
			}
		} else {
			assert (notice.getAccessType() == AccessNotice.WRITE);
			int address = notice.getAddress();
			if (LED_START <= address) {
				int value = notice.getValue();
				int length = notice.getLength();
				ledMemory.setPixel(address - LED_START, value, length);
			}
		}
	}

	/**
	 * Method to reset display when the Reset button selected. Overrides
	 * inherited method that does nothing.
	 */
	protected void reset() {
		ledMemory.reset();
		updateDisplay();
	}

	/**
	 * Overrides default method, to provide a Help button for this tool/app.
	 */

	protected JComponent getHelpComponent() {
		final String helpContent = "LED Display Simulator "
				+ version
				+ "\n\n"
				+ "Use this program to simulate Memory-Mapped I/O (MMIO) for a LED display output "
				+ "device. It may be run either from MARS' Tools menu or as a stand-alone application. "
				+ "For the latter, simply write a driver to instantiate a "
				+ this.getClass().getName() + " object "
				+ "and invoke its go() method.\n" + "\n"
				+ "The arrow buttons can control movement, as well as the WASD keyboard keys.\n"
				+ "\n"
		/* + "Contact " + author + " with questions or comments.\n" */;
		JButton help = new JButton("Help");
		help.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				JTextArea ja = new JTextArea(helpContent);
				ja.setRows(10);
				ja.setColumns(40);
				ja.setLineWrap(true);
				ja.setWrapStyleWord(true);
				JOptionPane.showMessageDialog(theWindow, new JScrollPane(ja),
						"Simulating the LED Display",
						JOptionPane.INFORMATION_MESSAGE);
			}
		});
		return help;
	}

	/**
	 * Updates display immediately after each update (AccessNotice) is
	 * processed.
	 */
	protected void updateDisplay() {
		displayPanel.repaint();
	}

	static final Color Blue;
	static { Blue = new Color(51, 102, 255); }
	/**
	 * Class that represents the visualized LED Display.
	 */
	private class LEDDisplayPanel extends JPanel {

		static final long serialVersionUID = 1; /* To eliminate a warning about serializability. */

		// override default paintComponent method
		public void paintComponent(Graphics g) {
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);
			// super.paintComponent(g);
			for (int row = 0; row < N_ROWS; row++) {
				for (int column = 0; column < N_COLUMNS; column++) {
					int pixel = ledMemory.getPixel(column, row);
					switch (pixel & 7) {
					case 0:
						g.setColor(Color.DARK_GRAY);
						break;
					case 1:
						g.setColor(Color.RED);
						break;
					case 2:
						g.setColor(Color.ORANGE);
						break;
					case 3:
						g.setColor(Color.YELLOW);
						break;
					case 4:
						g.setColor(Color.GREEN);
						break;
					case 5:
						g.setColor(Blue);
						break;
					case 6:
						g.setColor(Color.MAGENTA);
						break;
					case 7:
						g.setColor(Color.WHITE);
						break;
					default:
						/* invalid color */
						break;
					}
					// g.fillOval(
					g.fillRect(
							column * CELL_WIDTH + CELL_PADDING,
							row * CELL_HEIGHT + CELL_PADDING,
							PIXEL_WIDTH,
							PIXEL_HEIGHT);
				}
			}
		}
	}

	/**
	 * Class that represents the internal graphics memory.
	 */
	private class GraphicsMemory {

		private byte[] theBytes;

		public GraphicsMemory(int size) {
			theBytes = new byte[size];
			Arrays.fill(theBytes, (byte) 0);
		}

		public void reset() {
			Arrays.fill(theBytes, (byte) 0);
		}

		public void setPixel(int address, int value, int length) {
			for (int i = 0; i < length; i++) {
				this.theBytes[address + i] = (byte) ((value >> (i * 8)) & 0xff);
			}
		}

		public int getPixel(int column, int row) {
			return this.theBytes[row * N_COLUMNS + column];
		}
	}

	/**
	 * Class that represents the visualized keypad and deals with key presses.
	 */
	private class KeypadPanel extends JPanel implements ActionListener {

		static final long serialVersionUID = 1; /* To eliminate a warning about serializability. */

		protected JButton westButton, northButton, eastButton, southButton, actionButton;

		private boolean upHeld, downHeld, leftHeld, rightHeld, actionHeld;

		public KeypadPanel() {
			// create arrow buttons
			northButton = new BasicArrowButton(SwingConstants.NORTH);
			westButton = new BasicArrowButton(SwingConstants.WEST);
			eastButton = new BasicArrowButton(SwingConstants.EAST);
			southButton = new BasicArrowButton(SwingConstants.SOUTH);

			actionButton = new JButton("b");
			// handle button events
			northButton.addActionListener(this);
			westButton.addActionListener(this);
			eastButton.addActionListener(this);
			southButton.addActionListener(this);

			actionButton.addActionListener(this);
			// add buttons to this container
			setLayout(new GridLayout(3, 3));
			add(new JPanel());
			add(northButton);
			add(new JPanel());
			add(westButton);
			add(actionButton);
			add(eastButton);
			add(new JPanel());
			add(southButton);
			add(new JPanel());

		}

		/**
		 * This function handles the events for when a button is pressed, or a key is pressed.
		 */
		public void actionPerformed(ActionEvent e) {

			JOptionPane.showMessageDialog(this, "Please use the W, A, S, D, and B keys on your keyboard.");

			// JB: lol copout
			// if (e.getSource() instanceof BasicArrowButton){
			// 	int direction = ((BasicArrowButton) e.getSource()).getDirection();
			// 	switch (direction) {
			// 	case SwingConstants.NORTH:
			// 		updateInput(KeyEvent.VK_KP_UP);
			// 		break;
			// 	case SwingConstants.WEST:
			// 		updateInput(KeyEvent.VK_KP_LEFT);
			// 		break;
			// 	case SwingConstants.EAST:
			// 		updateInput(KeyEvent.VK_KP_RIGHT);
			// 		break;
			// 	case SwingConstants.SOUTH:
			// 		updateInput(KeyEvent.VK_KP_DOWN);
			// 		break;
			// 	}
			// } else if (e.getSource() instanceof JButton)
			// {
			// 	//If it's not a direction button, it must be the action button.
			// 		updateInput(KeyEvent.VK_B);

			// } else {
			// 	//error!
			// }
		}

		public void pressKey(int keyCode) {
			switch(keyCode) {
				case KeyEvent.VK_KP_UP:    if(upHeld)     return; upHeld = true;     break;
				case KeyEvent.VK_KP_DOWN:  if(downHeld)   return; downHeld = true;   break;
				case KeyEvent.VK_KP_LEFT:  if(leftHeld)   return; leftHeld = true;   break;
				case KeyEvent.VK_KP_RIGHT: if(rightHeld)  return; rightHeld = true;  break;
				case KeyEvent.VK_B:        if(actionHeld) return; actionHeld = true; break;
			}
			updateInput(keyCode);
		}

		public void releaseKey(int keyCode) {
			switch(keyCode) {
				case KeyEvent.VK_KP_UP:    if(!upHeld)     return; upHeld = false;     break;
				case KeyEvent.VK_KP_DOWN:  if(!downHeld)   return; downHeld = false;   break;
				case KeyEvent.VK_KP_LEFT:  if(!leftHeld)   return; leftHeld = false;   break;
				case KeyEvent.VK_KP_RIGHT: if(!rightHeld)  return; rightHeld = false;  break;
				case KeyEvent.VK_B:        if(!actionHeld) return; actionHeld = false; break;
			}
			updateInput(keyCode + 0x10); // JB: lol hax
		}

		public void updateInput(int keyCode) {

			//System.err.println("Responding to keyCode = " + keyCode);

			int updatedReceiverControl = readyBitSet(RECEIVER_CONTROL);
			updateMMIOControlAndData(RECEIVER_CONTROL, updatedReceiverControl,
					RECEIVER_DATA, keyCode & 0x00000ff);
			if (updatedReceiverControl != 1
					&& (Coprocessor0.getValue(Coprocessor0.STATUS) & 2) == 0
					&& (Coprocessor0.getValue(Coprocessor0.STATUS) & 1) == 1) {
				// interrupt-enabled bit is set in both Receiver Control and in
				// Coprocessor0 Status register, and Interrupt Level Bit is 0,
				// so trigger external interrupt.
				mars.simulator.Simulator.externalInterruptingDevice = Exceptions.EXTERNAL_INTERRUPT_KEYBOARD;
			}
		}

	}
}
