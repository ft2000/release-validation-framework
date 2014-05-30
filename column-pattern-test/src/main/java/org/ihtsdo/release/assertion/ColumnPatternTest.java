package org.ihtsdo.release.assertion;

import org.ihtsdo.release.assertion._1_0.ColumnPatternTestConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// TODO: Unit Test this class thoroughly
public class ColumnPatternTest {

	private final ColumnPatternTestConfiguration configuration;
	private final File rf2FilesDirectory;
	private Map<String, Pattern> regexCache;

	private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
	private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{8}$");
	private static final Pattern COMPONENT_ID_PATTERN = Pattern.compile("^\\d{6,18}$");

	private static final String UTF_8 = "UTF-8";
	private static final Logger LOGGER = LoggerFactory.getLogger(ColumnPatternTest.class);

	public ColumnPatternTest(ColumnPatternTestConfiguration configuration, File rf2FilesDirectory) {
		this.configuration = configuration;
		this.rf2FilesDirectory = rf2FilesDirectory;
		regexCache = testConfigurationByPrecompilingRegexPatterns(configuration);
	}

	public void runTests() {

		// Stats
		Date startTime = new Date();
		int filesTested = 0;
		long linesTested = 0;

		// TODO: Report errors in a machine readable way for error organisation/navigation.
		for (ColumnPatternTestConfiguration.File file : configuration.getFile()) {
			String fileName = file.getName();

			File rf2File = new File(rf2FilesDirectory, fileName);
			if (rf2File.isFile()) {
				try {
					List<ColumnPatternTestConfiguration.File.Column> columns = file.getColumn();
					int expectedColumnCount = columns.size();
					BufferedReader reader = Files.newBufferedReader(rf2File.toPath(), Charset.forName(UTF_8));
					filesTested++;
					String line;
					long lineNumber = 0;

					// Variables outside loop to force memory reuse.
					String[] columnData;
					String value;

					while ((line = reader.readLine()) != null) {
						linesTested++;
						lineNumber++;
						columnData = line.split("\t");
						int columnCount = columnData.length;
						if (columnCount == expectedColumnCount) {
							for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
								ColumnPatternTestConfiguration.File.Column column = columns.get(columnIndex);
								value = columnData[columnIndex];
								if (lineNumber == 1) {
									// Test header value
									testHeaderValue(value, column);
								} else {
									// Test data value
									testDataValue(lineNumber, value, column);
								}
							}
						} else {
							LOGGER.error("Columns missing on line {}: expected {}, actual {}", lineNumber, columnCount, expectedColumnCount);
						}
					}
				} catch (IOException e) {
					LOGGER.error("Problem reading file {}", fileName, e);
				}
			} else {
				LOGGER.error("Unable to locate file {}", fileName);
			}
		}
		LOGGER.info("{} files and {} lines tested in {} milliseconds.", filesTested, linesTested, (new Date().getTime() - startTime.getTime()));
	}

	private void testDataValue(long lineNumber, String value, ColumnPatternTestConfiguration.File.Column column) {
		// UUID
		if (column.getUuid() != null) {
			if (!UUID_PATTERN.matcher(value).matches()) {
				LOGGER.error("Value does not match UUID pattern on line {}, column name '{}': value '{}'", lineNumber, column.getName(), value);
			}
		}

		// Boolean
		if (column.getBoolean() != null) {
			if (!"1".equals(value) && !"0".equals(value)) {
				LOGGER.error("Value does not match Boolean pattern on line {}, column name '{}': value '{}'", lineNumber, column.getName(), value);
			}
		}

		// Value
		String expectedValue = column.getValue();
		if (expectedValue != null) {
			if (!expectedValue.equals(value)) {
				LOGGER.error("Value does not match Expected value on line {}, column name '{}': expected '{}', actual '{}'", lineNumber, column.getName(), expectedValue, value);
			}
		}

		// Date Stamp
		ColumnPatternTestConfiguration.File.Column.DateStamp dateStamp = column.getDateStamp();
		if (dateStamp != null) {
			if (!DATE_PATTERN.matcher(value).matches()) {
				LOGGER.error("Value does not match Date Stamp pattern on line {}, column name '{}': value '{}'", lineNumber, column.getName(), value);
			} else {
				BigInteger maxDate = dateStamp.getMaxDate();
				if (maxDate != null) {
					Integer valueInt = Integer.valueOf(value);
					int maxDateInt = maxDate.intValue();
					if (valueInt > maxDateInt) {
						LOGGER.error("Value is a date after the maximum date on line {}, column name '{}': maximum date '', actual date '{}'", lineNumber, column.getName(), maxDateInt, value);
					}
				}
			}
		}

		// Component SCT ID
		if (column.getSctid() != null) {
			if (!COMPONENT_ID_PATTERN.matcher(value).matches()) {
				LOGGER.error("Value does not match Component ID pattern on line {}, column name '{}': value '{}'", lineNumber, column.getName(), value);
			}
		}

		// Regex
		String regex = column.getRegex();
		if (regex != null) {
			if (regexCache.get(regex).matcher(value).matches()) {
				LOGGER.error("Value does not match custom regex pattern on line {}, column name '{}': pattern '{}', value '{}'", lineNumber, column.getName(), regex, value);
			}
		}
	}

	private void testHeaderValue(String value, ColumnPatternTestConfiguration.File.Column column) {
		String expectedColumnName = column.getName();
		if (!expectedColumnName.equals(value)) {
			LOGGER.error("Column name does not match expected value: expected '{}', actual '{}'", expectedColumnName, value);
			// TODO: Should we stop testing the file if we reach this point?
		}
	}

	private Map<String, Pattern> testConfigurationByPrecompilingRegexPatterns(ColumnPatternTestConfiguration configuration) {
		Map<String, Pattern> regexCache = new HashMap<>();
		for (ColumnPatternTestConfiguration.File file : configuration.getFile()) {
			for (ColumnPatternTestConfiguration.File.Column column : file.getColumn()) {
				String regex = column.getRegex();
				if (regex != null) {
					try {
						Pattern compiledRegex = Pattern.compile(regex);
						regexCache.put(regex, compiledRegex);
					} catch (PatternSyntaxException e) {
						LOGGER.error("Regex invalid for file {} column {}", file.getName(), column.getName(), e);
					}
				}
			}
		}
		return regexCache;
	}

}
