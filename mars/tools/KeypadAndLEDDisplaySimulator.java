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


	private static String version = "Version 1.1 64x64";
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
	private static final int CELL_WIDTH = 8;
	private static final int CELL_HEIGHT = 8;
	private static final int CELL_PADDING = 0;
	private static final int PIXEL_WIDTH = CELL_WIDTH - 1 * CELL_PADDING;
	private static final int PIXEL_HEIGHT = CELL_HEIGHT - 1 * CELL_PADDING;
	private static final int DISPLAY_WIDTH = (N_COLUMNS * CELL_WIDTH) + (2 * CELL_PADDING);
	private static final int DISPLAY_HEIGHT = (N_ROWS * CELL_HEIGHT) + (2 * CELL_PADDING);
	private JPanel keypadAndDisplayArea;
	private JPanel displayPanel;
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
		displayPanel.setPreferredSize(new Dimension(DISPLAY_WIDTH, DISPLAY_HEIGHT));

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
			displayPanel.repaint();
	}

	protected void reset()
	{
		GraphicsMemory.reset();
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

	protected void updateDisplay()
	{
		displayPanel.repaint();
	}

	static final Color[] PixelColors = new Color[]
	{
		Color.DARK_GRAY,
		Color.RED,
		Color.ORANGE,
		Color.YELLOW,
		Color.GREEN,
		new Color(51, 102, 255),
		Color.MAGENTA,
		Color.WHITE,
	};

		// private static int num = 0;
	private class LEDDisplayPanel extends JPanel
	{
		public void paintComponent(Graphics g)
		{
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, DISPLAY_WIDTH, DISPLAY_HEIGHT);

			for(int row = 0; row < N_ROWS; row++)
			{
				for(int column = 0; column < N_COLUMNS; column++)
				{
					int pixel = GraphicsMemory.getPixel(column, row);
					g.setColor(PixelColors[pixel & 7]);
					g.fillRect(
						column * CELL_WIDTH + CELL_PADDING,
						row * CELL_HEIGHT + CELL_PADDING,
						PIXEL_WIDTH,
						PIXEL_HEIGHT);
				}
			}

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

	private static class GraphicsMemory
	{
		public static void reset()
		{
		}

		public static int getPixel(int column, int row)
		{
			int addr = LED_START + row * N_COLUMNS + column;
			int bytes = 0;

			try
			{
				bytes = Globals.memory.getWordNoNotify(addr & ~3);
			}
			catch(AddressErrorException aee){}

			int offs = addr & 3;
			return (bytes >> (24 - (offs * 8))) & 0xFF;
		}
	}
}
