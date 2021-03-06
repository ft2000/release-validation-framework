package org.ihtsdo.rvf.validation.log.impl;

import org.ihtsdo.rvf.validation.log.ValidationLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationLogImpl implements ValidationLog {

	private final Logger logger;
	private Class subject;

	public ValidationLogImpl(Class subject) {
		this.subject = subject;
		logger = LoggerFactory.getLogger(subject);
	}

	@Override
	public void assertionError(String message, Object... object) {
		logger.error(message, object);
	}

	@Override
	public void configurationError(String message, Object... object) {
		logger.error(message, object);
	}

	@Override
	public void executionError(String message, Object... object) {
		logger.error(message, object);
	}

	@Override
	public void info(String message, Object... object) {
		logger.info(message, object);
	}

}
