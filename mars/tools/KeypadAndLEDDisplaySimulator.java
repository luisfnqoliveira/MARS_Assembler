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
	private static String heading = "Click this window and use arrow keys and B!";
	private static final int PIXEL_BITS = 8;
	private static final int PIXELS_PER_BYTE = 8 / PIXEL_BITS;
	private static final int PIXEL_MASK = 0xFF;
	private static final int N_COLUMNS = 64;
	private static final int N_ROWS = 64;
	private static final int SCREEN_UPDATE = Memory.memoryMapBaseAddress;
	private static final int KEY_STATE = SCREEN_UPDATE + Memory.WORD_LENGTH_BYTES;
	private static final int LED_START = KEY_STATE + Memory.WORD_LENGTH_BYTES;
	private static final int LED_END = LED_START + N_ROWS * (N_COLUMNS / PIXELS_PER_BYTE);
	private static final int LED_BUFFER_START = LED_END + 4096;
	private static final int LED_BUFFER_END = LED_BUFFER_START + N_ROWS * (N_COLUMNS / PIXELS_PER_BYTE);
	private JPanel keypadAndDisplayArea;
	private LEDDisplayPanel displayPanel;
	private int keyState;

	private static final int KEY_U = 1;
	private static final int KEY_D = 2;
	private static final int KEY_L = 4;
	private static final int KEY_R = 8;
	private static final int KEY_B = 16;

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
		keypadAndDisplayArea = new JPanel();
		displayPanel = new LEDDisplayPanel();

		keypadAndDisplayArea.add(displayPanel);

		/* This is so hacky and verbose. I don't know of a better way. */
		// JB: I feel you, Jose. "Verbose" is the best adjective for Java.
		InputMap map = keypadAndDisplayArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, false), "LeftKeyPressed"   );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, false), "RightKeyPressed"  );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,    0, false), "UpKeyPressed"     );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,  0, false), "DownKeyPressed"   );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_B,     0, false), "ActionKeyPressed" );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT,  0, true ), "LeftKeyReleased"  );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0, true ), "RightKeyReleased" );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP,    0, true ), "UpKeyReleased"    );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,  0, true ), "DownKeyReleased"  );
		map.put(KeyStroke.getKeyStroke(KeyEvent.VK_B,     0, true ), "ActionKeyReleased");

		ActionMap actions = keypadAndDisplayArea.getActionMap();
		actions.put("LeftKeyPressed",    new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState | KEY_L);  } });
		actions.put("RightKeyPressed",   new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState | KEY_R);  } });
		actions.put("UpKeyPressed",      new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState | KEY_U);  } });
		actions.put("DownKeyPressed",    new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState | KEY_D);  } });
		actions.put("ActionKeyPressed",  new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState | KEY_B);  } });
		actions.put("LeftKeyReleased",   new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState & ~KEY_L); } });
		actions.put("RightKeyReleased",  new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState & ~KEY_R); } });
		actions.put("UpKeyReleased",     new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState & ~KEY_U); } });
		actions.put("DownKeyReleased",   new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState & ~KEY_D); } });
		actions.put("ActionKeyReleased", new AbstractAction() {
			public void actionPerformed(ActionEvent e) { changeKeyState(keyState & ~KEY_B); } });

		return keypadAndDisplayArea;
	}

	@Override
	protected void initializePostGUI() {
		connectButton.addActionListener((e) -> {
			displayPanel.repaint();
		});
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
		new Color(15, 15, 15),
		Color.RED,
		Color.ORANGE,
		Color.YELLOW,
		Color.GREEN,
		new Color(51, 102, 255),
		Color.MAGENTA,
		Color.WHITE,
	};

	private class LEDDisplayPanel extends JPanel
	{
		private int cellWidth = 8;
		private int cellHeight = 8;
		private int cellPadding = 0;
		private int pixelWidth;
		private int pixelHeight;
		private int displayWidth;
		private int displayHeight;

		public boolean shouldClear = false;
		private boolean drawGridLines = false;

		public LEDDisplayPanel() {
			System.out.println(Color.DARK_GRAY);
			this.recalcSizes();
		}

		private void recalcSizes() {
			pixelWidth = cellWidth - 1 * cellPadding;
			pixelHeight = cellHeight - 1 * cellPadding;
			displayWidth = (N_COLUMNS * cellWidth) + (2 * cellPadding);
			displayHeight = (N_ROWS * cellHeight) + (2 * cellPadding);
			this.setPreferredSize(new Dimension(displayWidth, displayHeight));
		}

		public void setGridLinesEnabled(boolean e) {
			if(e != drawGridLines) {
				drawGridLines = e;

				if(drawGridLines) {
					cellWidth = 7;
					cellHeight = 7;
					cellPadding = 1;
				} else {
					cellWidth = 8;
					cellHeight = 8;
					cellPadding = 0;
				}

				this.recalcSizes();
				this.repaint();
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

			g.setColor(Color.BLACK);

			int ptr = LED_BUFFER_START;

			try
			{
				for(int row = 0; row < N_ROWS; row++)
				{
					int y = row * cellHeight + cellPadding;

					for(int col = 0, x = 0; col < N_COLUMNS; col += 4, ptr += 4)
					{
						int pixel = Globals.memory.getWordNoNotify(ptr);

						g.setColor(PixelColors[pixel & 7]);
						g.fillRect(x, y, pixelWidth, pixelHeight);
						x += cellWidth + cellPadding;
						g.setColor(PixelColors[(pixel >> 8) & 7]);
						g.fillRect(x, y, pixelWidth, pixelHeight);
						x += cellWidth + cellPadding;
						g.setColor(PixelColors[(pixel >> 16) & 7]);
						g.fillRect(x, y, pixelWidth, pixelHeight);
						x += cellWidth + cellPadding;
						g.setColor(PixelColors[(pixel >> 24) & 7]);
						g.fillRect(x, y, pixelWidth, pixelHeight);
						x += cellWidth + cellPadding;
					}
				}
			}
			catch(AddressErrorException aee)
			{
				System.out.println("Tool author specified incorrect MMIO address!" + aee);
				System.exit(0);
			}
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
