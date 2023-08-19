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
public class KeypadAndLEDDisplaySimulator extends AbstractMarsToolAndApplication
{

	static final long serialVersionUID = 1; /* To eliminate a warning about serializability. */

	private static String version = "Version 1.2 64x64";
	private static String title = "Keypad and LED Display MMIO Simulator";
	private static String heading = "Click this window and use arrow keys and Z, X, C, B!";
	private static final int N_COLUMNS = 64;
	private static final int N_ROWS = 64;
	private static final int SCREEN_UPDATE = Memory.memoryMapBaseAddress;
	private static final int KEY_STATE = SCREEN_UPDATE + Memory.WORD_LENGTH_BYTES;
	private static final int LED_START = KEY_STATE + Memory.WORD_LENGTH_BYTES;
	private static final int LED_END = LED_START + N_ROWS * N_COLUMNS;

	// the 4096 is there to give a "buffer zone" between the user-written area and the
	// display buffer. this way, writes past the end of the display will be invisible.
	// ...it's just to give the students a little leeway. :P
	private static final int LED_BUFFER_START = LED_END + 4096;
	private static final int LED_BUFFER_END = LED_BUFFER_START + N_ROWS * N_COLUMNS;
	private JPanel panel;
	private LEDDisplayPanel displayPanel;
	private int keyState;

	private static final int KEY_U = 1;
	private static final int KEY_D = 2;
	private static final int KEY_L = 4;
	private static final int KEY_R = 8;
	private static final int KEY_B = 16;
	private static final int KEY_Z = 32;
	private static final int KEY_X = 64;
	private static final int KEY_C = 128;

	public KeypadAndLEDDisplaySimulator(String title, String heading)
	{
		super(title, heading);
	}

	public KeypadAndLEDDisplaySimulator()
	{
		super(title + ", " + version, heading);
	}

	public static void main(String[] args)
	{
		new KeypadAndLEDDisplaySimulator(title + " stand-alone, " + version,
										 heading).go();
	}

	public String getName()
	{
		return "Keypad and LED Display Simulator";
	}

	protected void addAsObserver()
	{
		addAsObserver(SCREEN_UPDATE, KEY_STATE);
	}

	protected JComponent buildMainDisplayArea()
	{
		panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		displayPanel = new LEDDisplayPanel();

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

		displayPanel.addKeyListener(new KeyListener() {
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

		displayPanel.requestFocusInWindow();

		return panel;
	}

	@Override
	protected void initializePostGUI() {
		connectButton.addActionListener((e) -> {
			displayPanel.repaint();
		});

		JDialog dialog = (JDialog)this.theWindow;

		if(dialog != null) {
			dialog.setResizable(false);

			dialog.addWindowFocusListener(new WindowAdapter() {
				public void windowGainedFocus(WindowEvent e) {
					displayPanel.requestFocusInWindow();
				}
			});
		}
	}

	private void changeKeyState(int newState)
	{
		keyState = newState;

		if(!this.isBeingUsedAsAMarsTool || connectButton.isConnected())
		{
			try
			{
				Globals.memory.setRawWord(KEY_STATE, newState);
			}
			catch(AddressErrorException aee)
			{
				System.out.println("Tool author specified incorrect MMIO address!" + aee);
				System.exit(0);
			}
		}
	}

	protected void processMIPSUpdate(Observable memory, AccessNotice accessNotice)
	{
		MemoryAccessNotice notice = (MemoryAccessNotice) accessNotice;

		if(notice.getAccessType() == AccessNotice.WRITE && notice.getAddress() == SCREEN_UPDATE)
		{
			displayPanel.shouldClear = notice.getValue() != 0;
			shouldRedraw = true;

			// Copy values from memory to internal buffer, reset if we must.
			try
			{
				// Ensure block for destination exists
				Globals.memory.setRawWord(LED_BUFFER_START, 0x0);
				Globals.memory.setRawWord(LED_BUFFER_END - 0x4, 0x0);
				Globals.memory.copyMMIOFast(LED_START, LED_BUFFER_START, LED_END - LED_START);
			}
			catch(AddressErrorException aee)
			{
				System.out.println("Tool author specified incorrect MMIO address!" + aee);
				System.exit(0);
			}

			if(displayPanel.shouldClear)
			{
				displayPanel.shouldClear = false;
				resetGraphicsMemory();
			}
		}
	}

	protected void reset()
	{
		resetGraphicsMemory();
		shouldRedraw = true;
		updateDisplay();
	}

	protected JComponent getHelpComponent()
	{
		final String helpContent = "LED Display Simulator "
			+ version
			+ "\n\n"
			+ "Use this program to simulate Memory-Mapped I/O (MMIO) for a LED display output "
			+ "device. It may be run either from MARS' Tools menu or as a stand-alone application. "
			+ "For the latter, simply write a driver to instantiate a "
			+ this.getClass().getName() + " object "
			+ "and invoke its go() method.\n" + "\n"
			+ "The arrow keys can control movement.\n"
			+ "\n"
			/* + "Contact " + author + " with questions or comments.\n" */;
		JButton help = new JButton("Help");
		help.addActionListener(new ActionListener()
		{
			public void actionPerformed(ActionEvent e)
			{
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

	private boolean shouldRedraw = true;

	protected void updateDisplay()
	{
		if(shouldRedraw)
		{
			shouldRedraw = false;
			displayPanel.repaint();
		}
	}

	static final Color[] PixelColors = new Color[]
	{
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

	private class LEDDisplayPanel extends JPanel
	{
		private static final int CELL_DEFAULT_SIZE = 8;
		private static final int CELL_ZOOMED_SIZE = 12;
		private static final int COLOR_MASK = 15;

		private int cellSize = CELL_DEFAULT_SIZE;
		private int cellPadding = 0;
		private int pixelSize;
		private int displayWidth;
		private int displayHeight;

		public boolean shouldClear = false;
		private boolean drawGridLines = false;
		private boolean zoomed = false;

		public LEDDisplayPanel() {
			this.recalcSizes();

			this.setFocusable(true);
			this.setFocusTraversalKeysEnabled(false);
			this.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent e) {
					requestFocusInWindow();
				}
			});

			this.addFocusListener(new FocusListener() {
				public void focusGained(FocusEvent e) {
					// TODO: set a flag saying we have focus
				}

				public void focusLost(FocusEvent e) {
					// TODO: set a flag saying we DON'T have focus
				}
			});
		}

		private void recalcSizes() {
			cellSize = cellSize = (zoomed ? CELL_ZOOMED_SIZE : CELL_DEFAULT_SIZE);
			cellPadding = drawGridLines ? 1 : 0;
			pixelSize = cellSize - cellPadding;
			pixelSize = cellSize - cellPadding;
			displayWidth = (N_COLUMNS * cellSize);
			displayHeight = (N_ROWS * cellSize);
			this.setPreferredSize(new Dimension(displayWidth, displayHeight));
		}

		public void setGridLinesEnabled(boolean e) {
			if(e != drawGridLines) {
				drawGridLines = e;
				this.recalcSizes();
			}
		}

		public void setZoomed(boolean e) {
			if(e != zoomed) {
				zoomed = e;
				this.recalcSizes();
			}
		}

		public void paintComponent(Graphics g)
		{
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

			int ptr = LED_BUFFER_START;

			try
			{
				for(int row = 0; row < N_ROWS; row++)
				{
					int y = row * cellSize;

					for(int col = 0, x = 0; col < N_COLUMNS; col += 4, ptr += 4)
					{
						int pixel = Globals.memory.getWordNoNotify(ptr);

						g.setColor(PixelColors[pixel & COLOR_MASK]);
						g.fillRect(x, y, pixelSize, pixelSize);
						x += cellSize;
						g.setColor(PixelColors[(pixel >> 8) & COLOR_MASK]);
						g.fillRect(x, y, pixelSize, pixelSize);
						x += cellSize;
						g.setColor(PixelColors[(pixel >> 16) & COLOR_MASK]);
						g.fillRect(x, y, pixelSize, pixelSize);
						x += cellSize;
						g.setColor(PixelColors[(pixel >> 24) & COLOR_MASK]);
						g.fillRect(x, y, pixelSize, pixelSize);
						x += cellSize;
					}
				}
			}
			catch(AddressErrorException aee)
			{
				System.out.println("Tool author specified incorrect MMIO address!" + aee);
				System.exit(0);
			}

			// TODO: if we don't have focus, draw an overlay saying to click on the display
		}
	}

	private static void resetGraphicsMemory()
	{
		try
		{
			Globals.memory.zeroMMIOFast(LED_START, LED_END - LED_START);
		}
		catch(AddressErrorException aee)
		{
			System.out.println("Tool author specified incorrect MMIO address!" + aee);
			System.exit(0);
		}
	}
}
