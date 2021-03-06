package org.ihtsdo.rvf.controller;

import org.ihtsdo.rvf.entity.Test;
import org.ihtsdo.rvf.entity.TestRunItem;
import org.ihtsdo.rvf.execution.service.AssertionExecutionService;
import org.ihtsdo.rvf.service.EntityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/tests")
public class TestController {

    @Autowired
    private EntityService entityService;
    @Autowired
    private AssertionExecutionService assertionExecutionService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public List<Test> getTests() {
        return entityService.findAll(Test.class);
    }

    @RequestMapping(value = "{id}", method = RequestMethod.GET)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Test getTest(@PathVariable Long id) {
        return (Test) entityService.find(Test.class, id);
    }

    @RequestMapping(value = "{id}", method = RequestMethod.DELETE)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Test deleteTest(@PathVariable Long id) {
        Test test = (Test) entityService.find(Test.class, id);
        entityService.delete(test);
        return test;
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    @ResponseBody
    @ResponseStatus(HttpStatus.CREATED)
    public Test createTest(@RequestBody Test test) {
        return (Test) entityService.create(test);
    }

    @RequestMapping(value = "{id}", method = RequestMethod.PUT)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Test updateTest(@PathVariable Long id,
                                     @RequestBody(required = false) Test test) {
        Test test1 = (Test) entityService.find(Test.class, id);
        test.setId(test1.getId());
        return (Test) entityService.update(test);
    }

    @RequestMapping(value = "count", method = RequestMethod.GET)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public Long countTests() {
        return entityService.count(Test.class);
    }

/*
 *  PGW: I don't think it's valid to execute a test without it being linked to an assertion.   Tests need to know what 
 *  assertion they belong to in order to report what rule is being broken.  
    @RequestMapping(value = "/{id}/run", method = RequestMethod.GET)
    @ResponseBody
    @ResponseStatus(HttpStatus.OK)
    public TestRunItem executeTest(@PathVariable Long id,
                                   @RequestParam Long runId, @RequestParam String prospectiveReleaseVersion,
                                   @RequestParam String previousReleaseVersion) {
        Test test1 = (Test) entityService.find(Test.class, id);
        return assertionExecutionService.executeTest(test1, runId, prospectiveReleaseVersion, previousReleaseVersion);
    } */
}
