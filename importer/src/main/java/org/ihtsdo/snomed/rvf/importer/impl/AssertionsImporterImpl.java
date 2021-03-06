package org.ihtsdo.snomed.rvf.importer.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;

import org.ihtsdo.rvf.entity.Assertion;
import org.ihtsdo.rvf.entity.ExecutionCommand;
import org.ihtsdo.rvf.entity.Test;
import org.ihtsdo.rvf.entity.TestType;
import org.ihtsdo.rvf.helper.Configuration;
import org.ihtsdo.snomed.rvf.importer.AssertionsImporter;
import org.ihtsdo.snomed.rvf.importer.helper.RvfRestClient;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.facebook.presto.sql.parser.SqlParser;
import com.facebook.presto.sql.parser.StatementSplitter;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An implementation of a {@link org.ihtsdo.snomed.rvf.importer.AssertionsImporter} that imports older
 * Release Assertion Toolkit content via XML and SQL files. The XML file defines the assertions and the SQL files are
 * used to populate the corresponding tests.
 */
@Service
public class AssertionsImporterImpl implements AssertionsImporter {

    private static final String CREATE_PROCEDURE = "CREATE PROCEDURE";
	private static final SqlParser SQL_PARSER = new SqlParser();
    private static final Logger logger = LoggerFactory.getLogger(AssertionsImporterImpl.class);
    @Autowired
    protected RvfRestClient restClient;
    protected ObjectMapper objectMapper = new ObjectMapper();
    protected Map<String, String> lookupMap = new HashMap<>();

    /**
     * No args constructor for IOC
     */
    public AssertionsImporterImpl() {
    }

    @Override
    public void importAssertionsFromFile(final String xmlFilePath, final String sqlResourcesFolderLocation){
        // get JDOM document from given manifest file
        final Document xmlDocument = getJDomDocumentFromFile(xmlFilePath);
        if(xmlDocument != null)
        {
            final XPathFactory factory = XPathFactory.instance();
            final XPathExpression expression = factory.compile("//script");
            final List<Element> scriptElements = expression.evaluate(xmlDocument);
            if(scriptElements.size() > 0)
            {
                // get various values from script element
                for(final Element element : scriptElements)
                {
                    final ResponseEntity responseEntity = createAssertionFromElement(element);
                    if(HttpStatus.CREATED == responseEntity.getStatusCode()){
                        try
                        {
                            final Assertion assertion = objectMapper.readValue(responseEntity.getBody().toString(), Assertion.class);
                            logger.info("Created assertion id : " + assertion.getId());
                            assert UUID.fromString(element.getAttributeValue("uuid")).equals(assertion.getUuid());
                            // get Sql file name from element and use it to add SQL test
                            final String sqlFileName = element.getAttributeValue("sqlFile");
                            logger.info("sqlFileName = " + sqlFileName);
                            String category = element.getAttributeValue("category");
                            /*
                                We know category is written as file-centric-validation, component-centric-validation, etc
                                We use this to generate the corresponding using folder name = category - validation
                              */
                            final int index = category.indexOf("validation");
                            if(index > -1)
                            {
                                category = category.substring(0, index-1);
                            }
                            logger.info("category = " + category);

                            final File sqlFile = new File(sqlResourcesFolderLocation+System.getProperty("file.separator")+category, sqlFileName);
                            final String sqlString = readStream(new FileInputStream(sqlFile));
                            // add test to assertion
                            addSqlTestToAssertion(assertion, sqlString);
                        }
                        catch (final FileNotFoundException e) {
                            logger.error("Nested exception is : " + e.getMessage());
                        }
                        catch (JsonMappingException | JsonParseException e) {
                            logger.warn("Error reading or parsing json. Nested exception is : " + e.getMessage());
                        }
                        catch (final Exception e) {
                            logger.warn("Error reading sql from input stream. Nested exception is : " + e.getMessage());
                        }
                    }
                    else{
                        logger.error("Error creating assertion");
                    }

                }

                // finally print all lookup map contents for debugging - //todo save somewhere?
                logger.info("lookupMap = " + lookupMap);
            }
            else{
                logger.error("There are no script elements to import in the XML file provided. Please note that the " +
                        "XML file should contain element named script");
            }
        }
        else{
            logger.warn("Error generating document from xml file passed : " + xmlFilePath);
        }
    }

    protected ResponseEntity createAssertionFromElement(final Element element){

        final String category = element.getAttributeValue("category");
        logger.info("category = " + category);
        final String uuid = element.getAttributeValue("uuid");
        logger.info("uuid = " + uuid);
        final String text = element.getAttributeValue("text");
        logger.info("text = " + text);
        final String sqlFileName = element.getAttributeValue("sqlFile");
        logger.info("sqlFileName = " + sqlFileName);

        // add entities using rest client
        final Assertion assertion = new Assertion();
        assertion.setUuid(UUID.fromString(uuid));
        assertion.setStatement(text);
        assertion.setName(text);
        assertion.setKeywords(category);

        try
        {
            String paramsString = objectMapper.writeValueAsString(assertion);
            paramsString = paramsString.replaceAll("\"id\":null,", "");
            return restClient.post("assertions", paramsString);
        }
        catch (final IOException e) {
            logger.warn("Nested exception is : " + e.fillInStackTrace());
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
    }

    protected ResponseEntity addSqlTestToAssertion(final Assertion assertion, final String sql){

        final List<String> statements = new ArrayList<>();
        final StatementSplitter splitter = new StatementSplitter(sql);
        if (splitter.getCompleteStatements() == null || splitter.getCompleteStatements().isEmpty()) {
        	logger.warn("SQL statements not ending with ;" + sql );
        }
        final StringBuilder storedProcedureSql = new StringBuilder();
        boolean storedProcedureFound = false;
        for(final StatementSplitter.Statement statement : splitter.getCompleteStatements())
        {
            String cleanedSql = statement.statement();
            if ( cleanedSql.startsWith(CREATE_PROCEDURE) || cleanedSql.startsWith(CREATE_PROCEDURE.toLowerCase())) {
            	storedProcedureFound = true;
            }
            // tokenise and process statement
            final StringTokenizer tokenizer = new StringTokenizer(cleanedSql);
            while(tokenizer.hasMoreTokens())
            {
                String token = tokenizer.nextToken();
                final Map<String, String> schemaMapping = getRvfSchemaMapping(token);
                if(schemaMapping.keySet().size() > 0){
                    // we know sometimes tokenizer messed up and leaves a trailing ), so we clena this up
                    if(token.endsWith(")")){
                        token = token.substring(0, token.length() - 1);
                    }
                    lookupMap.put(token, schemaMapping.get(token));
                    // now replace all instances with rvf mapping
                    cleanedSql = cleanedSql.replaceAll(token, schemaMapping.get(token));
                }
            }
            cleanedSql = cleanedSql.replaceAll("runid", "run_id");
            cleanedSql = cleanedSql.replaceAll("assertionuuid", "assertion_id");
            cleanedSql = cleanedSql.replaceAll("assertiontext", "assertion_text");
            logger.info("cleaned sql:" + cleanedSql);
            if (!storedProcedureFound) {
               statements.add(cleanedSql);
            } else {
            	storedProcedureSql.append(cleanedSql + ";\n");
            }
        }
        if (storedProcedureFound && storedProcedureSql.length() > 0) {
        	statements.add(storedProcedureSql.toString());
        	logger.debug("Stored proecure found:" + storedProcedureSql.toString());
        }

        // set configuration;
        final Configuration configuration = new Configuration();
        final ExecutionCommand command = new ExecutionCommand();
        command.setTemplate(sql);
        command.setCode("Execute me".getBytes());
        command.setConfiguration(configuration);
        command.setStatements(statements);
        final Test test = new Test();
        test.setType(TestType.SQL);
        test.setName(assertion.getName());
        test.setCommand(command);
        // we have to add as a list of tests, since api spec expects list of tests
        final List<Test> tests = new ArrayList<>();
        tests.add(test);

        try
        {
            return restClient.post("assertions/"+assertion.getId()+"/tests", objectMapper.writeValueAsString(tests));
        }
        catch (final IOException e) {
            logger.warn("Nested exception is : " + e.fillInStackTrace());
            return new ResponseEntity(HttpStatus.NOT_FOUND);
        }
    }

    protected static Document getJDomDocumentFromFile(final String fileName){

        try {
            final SAXBuilder sax = new SAXBuilder();
            return sax.build(fileName);
        }
        catch (JDOMException | IOException e) {
            logger.warn("Nested exception is : " + e.fillInStackTrace());
            return null;
        }
    }

    protected static String readStream(final InputStream is) throws Exception {
        final InputStreamReader reader = new InputStreamReader(is);
        final StringBuilder builder = new StringBuilder();
        final char buffer[] = new char[1024];
        // Wait for at least 1 byte (e.g. stdin)
        int n = reader.read(buffer);
        builder.append(buffer, 0, n);
        while(reader.ready()) {
            n = reader.read(buffer);
            builder.append(buffer, 0, n);
        }
        return builder.toString();
    }

    protected Map<String, String> getRvfSchemaMapping(String ratSchema){
        String rvfSchema = "";
        ratSchema = ratSchema.trim();
        final String originalRatSchema = ratSchema;
        boolean currOrPrevFound = false;
        if(ratSchema.startsWith("curr_")){
            rvfSchema = "<PROSPECTIVE>";
            // we strip the prefix - note we don't include _ in length since strings are 0 indexed
            ratSchema = ratSchema.substring("curr_".length());
            currOrPrevFound = true;
        }
        else if(ratSchema.startsWith("prev_")){
            rvfSchema = "<PREVIOUS>";
            // we strip the prefix - note we don't include _ in length since strings are 0 indexed
            ratSchema = ratSchema.substring("prev_".length());
            currOrPrevFound = true;
        }
        else if(ratSchema.startsWith("v_")){
            // finally process token that represents temp tables - starts with v_
            ratSchema = ratSchema.substring("v_".length());
            rvfSchema = "<TEMP>" + "." + ratSchema;
        }

        // hack to clean up conditions where tokenisation produces schema mappings with ) at the end
        if(currOrPrevFound && ratSchema.endsWith(")")){
            ratSchema = ratSchema.substring(0, ratSchema.lastIndexOf(")"));
        }

        // now process for release type suffix
        if(ratSchema.endsWith("_s")){
            // we strip the suffix
            ratSchema = ratSchema.substring(0, ratSchema.length() - 2);
            rvfSchema = rvfSchema + "." + ratSchema + "_<SNAPSHOT>";
        }
        else if(ratSchema.endsWith("_d")){
            // we strip the suffix
            ratSchema = ratSchema.substring(0, ratSchema.length() - 2);
            rvfSchema = rvfSchema + "." + ratSchema + "_<DELTA>";
        }
        else if(ratSchema.endsWith("_f")){
            // we strip the suffix
            ratSchema = ratSchema.substring(0, ratSchema.length() - 2);
            rvfSchema = rvfSchema + "." + ratSchema + "_<FULL>";
        }

        if (rvfSchema.length() > 0) {
            final Map<String, String> map = new HashMap<>();
            map.put(originalRatSchema, rvfSchema);

            return map;
        }
        else{
            return Collections.EMPTY_MAP;
        }

    }
}
