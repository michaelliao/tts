package com.itranswarp.tts;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.microsoft.cognitiveservices.speech.CancellationReason;
import com.microsoft.cognitiveservices.speech.ResultReason;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisCancellationDetails;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisOutputFormat;
import com.microsoft.cognitiveservices.speech.SpeechSynthesisResult;
import com.microsoft.cognitiveservices.speech.SpeechSynthesizer;

import javazoom.jl.player.Player;

/**
 * Test key: 841038f7dc6a43228e424c105f73b6dd
 * 
 * @author liaoxuefeng
 */
public class Main {
	public static void main(String[] args) {
		new TTSFrame();
	}
}

class TTSFrame extends JFrame {

	private final String KEY_REGION = "region";
	private final String KEY_SUB_KEY = "subkey";
	private final String KEY_OUTPUT_DIR = "outdir";
	private final String KEY_FORMAT = "format";
	private final String KEY_VOICE = "voice";

	private final List<String> REGIONS = readLines("/regions.txt");
	private final List<String> VOICES = readLines("/voices.txt");
	private final List<String> FORMATS = initFormats();

	private Properties properties;
	private JTextArea textArea;
	private File outputAudioFile;
	private Player player;

	public TTSFrame() {
		this.properties = loadProperties();
		setTitle("Text To Speech");
		setSize(640, 520);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				saveProperties(TTSFrame.this.properties);
			}
		});
		getContentPane().setLayout(new BoxLayout(getContentPane(), BoxLayout.Y_AXIS));
		{
			JPanel panel = createJPanelAsRow(getContentPane(), 0);
			panel.add(new JLabel("Region:"));
			JComboBox<String> selectRegion = new JComboBox<>(REGIONS.toArray(String[]::new));
			selectRegion.setSelectedItem(getRegion());
			selectRegion.addActionListener(e -> {
				String select = (String) selectRegion.getSelectedItem();
				setRegion(select);
			});
			panel.add(selectRegion);
		}
		{
			JPanel panel = createJPanelAsRow(getContentPane(), 0);
			panel.add(new JLabel("Subscription Key:"));
			JTextField textSubKey = new JTextField(getSubKey(), 30);
			textSubKey.getDocument().addDocumentListener(new DocumentChangedListener(() -> {
				setSubKey(textSubKey.getText());
			}));
			panel.add(textSubKey);
		}
		{
			JPanel panel = createJPanelAsRow(getContentPane(), 0);
			panel.add(new JLabel("Output Directory:"));
			JTextField textOutputDir = new JTextField(getOutputDir(), 30);
			textOutputDir.setEditable(false);
			panel.add(textOutputDir);
			JButton buttonChangeOutputDir = new JButton("Change");
			buttonChangeOutputDir.addActionListener(e -> {
				JFileChooser outputChooser = new JFileChooser();
				outputChooser.setCurrentDirectory(new File(getOutputDir()));
				outputChooser.setDialogTitle("Select Output Directory");
				outputChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
				outputChooser.setAcceptAllFileFilterUsed(false);
				if (outputChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
					String outputDir = outputChooser.getSelectedFile().getAbsolutePath();
					textOutputDir.setText(outputDir);
					setOutputDir(outputDir);
				}
			});
			panel.add(buttonChangeOutputDir);
		}
		{
			JPanel panel = createJPanelAsRow(getContentPane(), 0);
			panel.add(new JLabel("Audio Format:"));
			JComboBox<String> selectFormat = new JComboBox<>(FORMATS.toArray(String[]::new));
			selectFormat.setSelectedItem(getFormat());
			selectFormat.addActionListener(e -> {
				String select = (String) selectFormat.getSelectedItem();
				setFormat(select);
			});
			panel.add(selectFormat);
		}
		{
			JPanel panel = createJPanelAsRow(getContentPane(), 0);
			panel.add(new JLabel("Voice:"));
			JComboBox<String> selectVoice = new JComboBox<>(VOICES.toArray(String[]::new));
			selectVoice.setSelectedItem(getVoice());
			selectVoice.addActionListener(e -> {
				String voice = (String) selectVoice.getSelectedItem();
				setVoice(voice);
			});
			panel.add(selectVoice);
		}
		{
			JPanel panel = createJPanelAsRow(getContentPane(), 200);
			this.textArea = new JTextArea("Good morning. Have a nice day.", 20, 50);
			this.textArea.setLineWrap(true);
			JScrollPane jScrollPane = new JScrollPane(this.textArea);
			panel.add(jScrollPane);
		}
		{
			JPanel panel = createJPanelAsRow(getContentPane(), 0);
			JButton buttonTTS = new JButton("Text To Speech");
			panel.add(buttonTTS);
			URL loadingUrl = getClass().getResource("/loading.gif");
			JLabel loadingLabel = new JLabel();
			loadingLabel.setIcon(new ImageIcon(loadingUrl));
			loadingLabel.setVisible(false);
			panel.add(loadingLabel);
			JButton play = new JButton("Play");
			panel.add(play);
			play.addActionListener(e -> {
				if (this.player != null) {
					this.player.close();
					this.player = null;
				}
				new Thread(() -> {
					try (var input = new BufferedInputStream(new FileInputStream(this.outputAudioFile))) {
						Player player = new Player(input);
						player.play();
						this.player = player;
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(null, ex.getMessage());
					}
				}).start();
			});
			play.setVisible(false);

			buttonTTS.addActionListener(e -> {
				loadingLabel.setVisible(true);
				buttonTTS.setEnabled(false);
				play.setVisible(false);
				String text = this.textArea.getText().strip();
				new Thread(() -> {
					textToSpeech(text);
					SwingUtilities.invokeLater(() -> {
						loadingLabel.setVisible(false);
						buttonTTS.setEnabled(true);
						play.setVisible(true);
					});
				}).start();
			});
		}
		setVisible(true);
	}

	private JPanel createJPanelAsRow(Container c, int height) {
		JPanel panel = new JPanel();
		panel.setPreferredSize(new Dimension(640, height == 0 ? 40 : height));
		panel.setLayout(new FlowLayout(FlowLayout.LEADING, 10, 10));
		c.add(panel);
		return panel;
	}

	private String getRegion() {
		String r = this.properties.getProperty(KEY_REGION);
		if (r == null || !REGIONS.contains(r)) {
			r = REGIONS.get(0);
		}
		return r;
	}

	private void setRegion(String region) {
		this.properties.put(KEY_REGION, region);
	}

	private String getSubKey() {
		return this.properties.getProperty(KEY_SUB_KEY, "");
	}

	private void setSubKey(String subKey) {
		this.properties.put(KEY_SUB_KEY, subKey);
	}

	private String getOutputDir() {
		return this.properties.getProperty(KEY_OUTPUT_DIR,
				new File(System.getProperty("user.home", ".")).getAbsolutePath());
	}

	private void setOutputDir(String outputDir) {
		this.properties.setProperty(KEY_OUTPUT_DIR, outputDir);
	}

	private String getVoice() {
		String r = this.properties.getProperty(KEY_VOICE);
		if (r == null || !VOICES.contains(r)) {
			r = VOICES.get(0);
		}
		return r;
	}

	private String getFormat() {
		String r = this.properties.getProperty(KEY_FORMAT);
		if (r == null || !FORMATS.contains(r)) {
			r = FORMATS.get(0);
		}
		return r;
	}

	private void setFormat(String format) {
		this.properties.setProperty(KEY_FORMAT, format);
	}

	private void setVoice(String voice) {
		this.properties.setProperty(KEY_VOICE, voice);
	}

	private Properties loadProperties() {
		Properties props = new Properties();
		try (Reader reader = new BufferedReader(new FileReader("./settings.properties", StandardCharsets.UTF_8))) {
			props.load(reader);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return props;
	}

	private void saveProperties(Properties props) {
		try (Writer writer = new BufferedWriter(new FileWriter("./settings.properties", StandardCharsets.UTF_8))) {
			props.store(writer, "ms tts configuration");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private List<String> readLines(String classpath) {
		try (var reader = new BufferedReader(
				new InputStreamReader(getClass().getResourceAsStream(classpath), StandardCharsets.UTF_8))) {
			return reader.lines().map(s -> s.strip()).filter(s -> !s.isEmpty() || s.startsWith("#")).sorted()
					.collect(Collectors.toList());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private List<String> initFormats() {
		List<String> list = new ArrayList<>();
		for (SpeechSynthesisOutputFormat format : SpeechSynthesisOutputFormat.values()) {
			if (format.name().endsWith("Mp3")) {
				list.add(format.name());
			}
		}
		Collections.sort(list);
		return list;
	}

	private String generateFileName(String dir, String text) {
		LocalDateTime now = LocalDateTime.now();
		for (;;) {
			String fileName = "tts-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss", Locale.US));
			if (!new File(dir, fileName + ".mp3").isFile()) {
				return fileName;
			}
			now = now.plusSeconds(1);
		}
	}

	private void textToSpeech(String text) {
		String region = getRegion();
		String subKey = getSubKey();
		String voice = getVoice();
		String format = getFormat();
		String output = getOutputDir();
		System.out.println(
				String.format("Generate TTS:\n  region = %s\n  subKey = %s\n  voice = %s\n  format=%s\n  output = %s",
						region, subKey, voice, format, output));
		if (subKey.isEmpty()) {
			JOptionPane.showMessageDialog(null, "Please fill the subscription key.");
			return;
		}
		if (text.isEmpty()) {
			JOptionPane.showMessageDialog(null, "Please fill the text.");
			return;
		}
		String fileName = generateFileName(output, text);
		File mp3File = new File(output, fileName + ".mp3");
		File txtFile = new File(output, fileName + ".txt");
		try {
			SpeechConfig config = SpeechConfig.fromSubscription(subKey, region);
			if (config == null) {
				throw new RuntimeException("Init SpeechConfig failed.");
			}
			config.setSpeechSynthesisLanguage(voice.substring(0, 5));
			config.setSpeechSynthesisVoiceName(voice);
			config.setSpeechSynthesisOutputFormat(SpeechSynthesisOutputFormat.valueOf(format));
			SpeechSynthesizer synth = new SpeechSynthesizer(config);
			Future<SpeechSynthesisResult> task = synth.SpeakTextAsync(text);
			SpeechSynthesisResult result = task.get();
			if (result.getReason() == ResultReason.SynthesizingAudioCompleted) {
				try (OutputStream out = new BufferedOutputStream(new FileOutputStream(mp3File))) {
					out.write(result.getAudioData());
				}
				try (Writer writer = new BufferedWriter(new FileWriter(txtFile, StandardCharsets.UTF_8))) {
					writer.write("# voice = " + voice + "\n");
					writer.write("# format = " + format + "\n");
					writer.write("\n");
					writer.write(text);
				}
				this.outputAudioFile = mp3File;
			} else if (result.getReason() == ResultReason.Canceled) {
				SpeechSynthesisCancellationDetails cancellation = SpeechSynthesisCancellationDetails.fromResult(result);
				System.out.println("CANCELED: Reason=" + cancellation.getReason());
				if (cancellation.getReason() == CancellationReason.Error) {
					JOptionPane.showMessageDialog(null,
							"ERROR " + cancellation.getErrorCode() + ": " + cancellation.getErrorDetails());
				}
			}
			result.close();
			synth.close();
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(null, "Generate failed: " + e.getMessage());
		}
	}
}
