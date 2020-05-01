package com.itranswarp.tts;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class DocumentChangedListener implements DocumentListener {

	final Runnable callback;

	public DocumentChangedListener(Runnable callback) {
		this.callback = callback;
	}

	@Override
	public void insertUpdate(DocumentEvent e) {
		this.callback.run();
	}

	@Override
	public void removeUpdate(DocumentEvent e) {
		this.callback.run();
	}

	@Override
	public void changedUpdate(DocumentEvent e) {
		this.callback.run();
	}
}
