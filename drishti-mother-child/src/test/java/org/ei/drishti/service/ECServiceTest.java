package org.ei.drishti.service;

import org.ei.drishti.contract.EligibleCoupleCloseRequest;
import org.ei.drishti.contract.FamilyPlanningUpdateRequest;
import org.ei.drishti.contract.OutOfAreaANCRegistrationRequest;
import org.ei.drishti.domain.EligibleCouple;
import org.ei.drishti.domain.FPProductInformation;
import org.ei.drishti.domain.form.FormSubmission;
import org.ei.drishti.repository.AllEligibleCouples;
import org.ei.drishti.service.formSubmissionHandler.ReportFieldsDefinition;
import org.ei.drishti.service.reporting.ECReportingService;
import org.ei.drishti.service.scheduling.ECSchedulingService;
import org.ei.drishti.util.FormSubmissionBuilder;
import org.ei.drishti.util.IdGenerator;
import org.ei.drishti.util.SafeMap;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.UUID.randomUUID;
import static org.ei.drishti.common.AllConstants.Report.REPORT_EXTRA_DATA_KEY_NAME;
import static org.ei.drishti.scheduler.DrishtiScheduleConstants.ECSchedulesConstants.EC_SCHEDULE_FP_COMPLICATION_MILESTONE;
import static org.ei.drishti.util.EasyMap.create;
import static org.ei.drishti.util.EasyMap.mapOf;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class ECServiceTest {
    @Mock
    private AllEligibleCouples allEligibleCouples;
    @Mock
    private ActionService actionService;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private ECReportingService reportingService;
    @Mock
    private ECSchedulingService schedulingService;
    @Mock
    private ReportFieldsDefinition reportFieldsDefinition;

    private ECService ecService;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        ecService = new ECService(allEligibleCouples, actionService, reportingService, idGenerator, schedulingService, reportFieldsDefinition);
    }

    @Test
    public void shouldRegisterEligibleCouple() throws Exception {
        FormSubmission submission = FormSubmissionBuilder.create()
                .withFormName("ec_registration")
                .withANMId("ANM X")
                .withEntityId("entity id 1")
                .addFormField("someKey", "someValue")
                .addFormField("currentMethod", "some method")
                .addFormField("isHighPriority", "yes")
                .addFormField("submissionDate", "2011-01-01")
                .addFormField("dmpaInjectionDate", "2010-12-20")
                .addFormField("numberOfOCPDelivered", "1")
                .addFormField("ocpRefillDate", "2010-12-25")
                .build();
        EligibleCouple eligibleCouple = new EligibleCouple("entity id 1", "0").withCouple("Wife 1", "Husband 1");
        when(allEligibleCouples.findByCaseId("entity id 1")).thenReturn(eligibleCouple);
        when(reportFieldsDefinition.get("ec_registration")).thenReturn(asList("someKey"));

        ecService.registerEligibleCouple(submission);

        verify(allEligibleCouples).update(eligibleCouple.withANMIdentifier("ANM X"));
        verify(reportingService).registerEC(new SafeMap(mapOf("someKey", "someValue")));
        verify(schedulingService).enrollToFPComplications("entity id 1", "some method", "yes", "2011-01-01");
        verify(schedulingService).enrollToRenewFPProducts("entity id 1", "some method", "2010-12-20", "1", "2010-12-25");
    }

    @Test
    public void shouldRegisterEligibleCoupleForOutOfAreaANC() throws Exception {
        Map<String, Map<String, String>> extraData = mapOf("details", Collections.<String, String>emptyMap());
        UUID ecCaseId = randomUUID();
        when(idGenerator.generateUUID()).thenReturn(ecCaseId);

        ecService.registerEligibleCoupleForOutOfAreaANC(new OutOfAreaANCRegistrationRequest("CASE X", "Wife 1", "Husband 1", "ANM X", "Village X", "SubCenter X", "PHC X", "TC 1", "2012-05-05", "9876543210"), extraData);

        EligibleCouple couple = new EligibleCouple(ecCaseId.toString(), "0").withCouple("Wife 1", "Husband 1")
                .withANMIdentifier("ANM X").withLocation("Village X", "SubCenter X", "PHC X").withDetails(extraData.get("details")).asOutOfArea();
        verify(allEligibleCouples).register(couple);
    }

    @Test
    public void shouldUpdateExistingDetailsBlobInECAndCreateAnActionForFPMethodUpdate() throws Exception {
        Map<String, String> existingDetails = mapOf("existingThing", "existingValue");
        EligibleCouple existingCoupleBeforeUpdate = new EligibleCouple("CASE X", "EC Number 1").withANMIdentifier("ANM X").withLocation("Village X", "SubCenter X", "PHC X").withDetails(existingDetails);

        Map<String, String> updatedDetails = create("currentMethod", "CONDOM").put("existingThing", "existingValue").map();
        EligibleCouple existingCoupleAfterUpdate = new EligibleCouple("CASE X", "EC Number 1").withANMIdentifier("ANM X").withLocation("Village X", "SubCenter X", "PHC X").withDetails(updatedDetails);

        when(allEligibleCouples.findByCaseId("CASE X")).thenReturn(existingCoupleBeforeUpdate);
        when(allEligibleCouples.updateDetails("CASE X", mapOf("currentMethod", "CONDOM"))).thenReturn(existingCoupleAfterUpdate);

        ecService.updateFamilyPlanningMethod(new FamilyPlanningUpdateRequest("CASE X", "ANM X"), mapOf("details", mapOf("currentMethod", "CONDOM")));

        verify(allEligibleCouples).updateDetails("CASE X", mapOf("currentMethod", "CONDOM"));
        verify(allEligibleCouples).findByCaseId("CASE X");
        verify(actionService).updateEligibleCoupleDetails("CASE X", "ANM X", updatedDetails);
    }

    @Test
    public void shouldReportFPMethodChangeWhenFPMethodIsUpdated() throws Exception {
        Map<String, String> existingDetails = mapOf("existingThing", "existingValue");
        EligibleCouple existingCoupleBeforeUpdate = new EligibleCouple("CASE X", "EC Number 1").withANMIdentifier("ANM X").withLocation("Village X", "SubCenter X", "PHC X").withDetails(existingDetails);
        Map<String, String> updatedDetails = create("currentMethod", "CONDOM").put("existingThing", "existingValue").map();
        EligibleCouple existingCoupleAfterUpdate = new EligibleCouple("CASE X", "EC Number 1").withANMIdentifier("ANM X").withLocation("Village X", "SubCenter X", "PHC X").withDetails(updatedDetails);
        when(allEligibleCouples.findByCaseId("CASE X")).thenReturn(existingCoupleBeforeUpdate);
        when(allEligibleCouples.updateDetails("CASE X", mapOf("currentMethod", "CONDOM"))).thenReturn(existingCoupleAfterUpdate);

        ecService.updateFamilyPlanningMethod(new FamilyPlanningUpdateRequest("CASE X", "ANM X"), create("details", mapOf("currentMethod", "CONDOM")).put(REPORT_EXTRA_DATA_KEY_NAME, mapOf("currentMethod", "CONDOM")).map());

        verify(reportingService).updateFamilyPlanningMethod(new SafeMap(mapOf("currentMethod", "CONDOM")));
    }

    @Test
    public void shouldUpdateFPComplicationsScheduleAndCloseAlertsWhenFPMethodIsUpdated() throws Exception {
        Map<String, String> existingDetails = mapOf("existingThing", "existingValue");
        EligibleCouple existingCoupleBeforeUpdate = new EligibleCouple("CASE X", "EC Number 1").withANMIdentifier("ANM X").withLocation("Village X", "SubCenter X", "PHC X").withDetails(existingDetails);
        Map<String, String> updatedDetails = create("currentMethod", "CONDOM").put("existingThing", "existingValue").map();
        EligibleCouple existingCoupleAfterUpdate = new EligibleCouple("CASE X", "EC Number 1").withANMIdentifier("ANM X").withLocation("Village X", "SubCenter X", "PHC X").withDetails(updatedDetails);
        when(allEligibleCouples.findByCaseId("CASE X")).thenReturn(existingCoupleBeforeUpdate);
        when(allEligibleCouples.updateDetails("CASE X", mapOf("currentMethod", "CONDOM"))).thenReturn(existingCoupleAfterUpdate);
        Map<String, Map<String, String>> extraDetails = create("details", mapOf("currentMethod", "CONDOM")).put(REPORT_EXTRA_DATA_KEY_NAME, mapOf("currentMethod", "CONDOM")).map();
        FamilyPlanningUpdateRequest request = new FamilyPlanningUpdateRequest("CASE X", "ANM X").withCurrentMethod("CONDOM");

        ecService.updateFamilyPlanningMethod(request, extraDetails);

        verify(schedulingService).updateFPComplications(request, existingCoupleAfterUpdate);
    }

    @Test
    public void shouldCloseFPScheduleAlertsWhenCoupleStartedUsingFPMethod() throws Exception {
        Map<String, String> details = mapOf("existingThing", "existingValue");
        EligibleCouple ec = new EligibleCouple("CASE X", "EC Number 1").withANMIdentifier("ANM X").withLocation("Village X", "SubCenter X", "PHC X").withDetails(details);
        when(allEligibleCouples.findByCaseId("CASE X")).thenReturn(ec);
        when(allEligibleCouples.updateDetails("CASE X", mapOf("currentMethod", "CONDOM"))).thenReturn(ec);
        Map<String, Map<String, String>> extraDetails = create("details", mapOf("currentMethod", "CONDOM")).put(REPORT_EXTRA_DATA_KEY_NAME, mapOf("currentMethod", "CONDOM")).map();
        FamilyPlanningUpdateRequest request = new FamilyPlanningUpdateRequest("CASE X", "ANM X").withCurrentMethod("CONDOM").withFPStartDate("2012-01-01");

        ecService.updateFamilyPlanningMethod(request, extraDetails);

        verify(actionService).markAlertAsClosed("CASE X", "ANM X", EC_SCHEDULE_FP_COMPLICATION_MILESTONE, "2012-01-01");
    }

    @Test
    public void shouldNotUpdateDetailsIfECIsNotFoundWhenFPMethodIsUpdated() throws Exception {
        when(allEligibleCouples.findByCaseId("CASE X")).thenReturn(null);

        ecService.updateFamilyPlanningMethod(new FamilyPlanningUpdateRequest("CASE X", "ANM X"), mapOf("details", mapOf("currentMethod", "CONDOM")));

        verify(allEligibleCouples).findByCaseId("CASE X");
        verifyNoMoreInteractions(allEligibleCouples);
        verifyZeroInteractions(actionService);
        verifyZeroInteractions(reportingService);
        verifyZeroInteractions(schedulingService);
    }

    @Test
    public void shouldSendDataToReportingServiceDuringReportFPChange() throws Exception {
        EligibleCouple ec = new EligibleCouple("entity id 1", "EC Number 1");
        when(allEligibleCouples.findByCaseId("entity id 1")).thenReturn(ec);
        FormSubmission submission = FormSubmissionBuilder.create()
                .withFormName("fp_change")
                .addFormField("someKey", "someValue")
                .build();
        when(reportFieldsDefinition.get("fp_change")).thenReturn(asList("someKey"));

        ecService.reportFPChange(submission);

        verify(allEligibleCouples).findByCaseId("entity id 1");
        verify(reportingService).fpChange(new SafeMap(mapOf("someKey", "someValue")));
    }

    @Test
    public void shouldNotDoAnythingDuringReportFPChangeIfNoECIsFound() throws Exception {
        when(allEligibleCouples.findByCaseId("entity id 1")).thenReturn(null);
        FormSubmission submission = FormSubmissionBuilder.create().build();

        ecService.reportFPChange(submission);

        verify(allEligibleCouples).findByCaseId("entity id 1");
        verifyZeroInteractions(reportingService);
        verifyZeroInteractions(schedulingService);
    }

    @Test
    public void shouldUpdateECSchedulesWhenFPMethodIsChanged() throws Exception {
        EligibleCouple ec = new EligibleCouple("entity id 1", "EC Number 1");
        when(allEligibleCouples.findByCaseId("entity id 1")).thenReturn(ec);
        FormSubmission submission = FormSubmissionBuilder.create()
                .withFormName("fp_change")
                .withANMId("anm id 1")
                .addFormField("currentMethod", "previous method")
                .addFormField("newMethod", "new method")
                .addFormField("submissionDate", "2011-01-01")
                .addFormField("familyPlanningMethodChangeDate", "2011-01-02")
                .addFormField("numberOfOCPDelivered", "1")
                .addFormField("numberOfCondomsSupplied", "20")
                .build();
        when(reportFieldsDefinition.get("fp_change")).thenReturn(asList("someKey"));

        ecService.reportFPChange(submission);

        verify(schedulingService).fpChange(
                new FPProductInformation("entity id 1", "anm id 1", "new method",
                        null, "1", null, "20", "2011-01-01", "previous method", "2011-01-02"));
    }

    @Test
    public void shouldUpdateECSchedulesWhenFPProductIsRenewed() throws Exception {
        EligibleCouple ec = new EligibleCouple("entity id 1", "EC Number 1");
        when(allEligibleCouples.findByCaseId("entity id 1")).thenReturn(ec);
        FormSubmission submission = FormSubmissionBuilder.create()
                .withFormName("renew_fp_product")
                .withANMId("anm id 1")
                .addFormField("currentMethod", "fp method")
                .addFormField("submissionDate", "2011-01-01")
                .addFormField("numberOfOCPDelivered", "1")
                .addFormField("ocpRefillDate", "2010-12-25")
                .addFormField("dmpaInjectionDate", "2010-12-20")
                .addFormField("numberOfCondomsSupplied", "20")
                .build();

        ecService.renewFPProduct(submission);

        verify(schedulingService).renewFPProduct(new FPProductInformation("entity id 1", "anm id 1", "fp method", "2010-12-20", "1", "2010-12-25", "20", "2011-01-01", null, ""));
    }

    @Test
    public void shouldNotDoAnythingDuringRenewFPProductWhenNoECIsFound() throws Exception {
        when(allEligibleCouples.findByCaseId("entity id 1")).thenReturn(null);
        FormSubmission submission = FormSubmissionBuilder.create().build();

        ecService.renewFPProduct(submission);

        verify(allEligibleCouples).findByCaseId("entity id 1");
        verifyZeroInteractions(reportingService);
        verifyZeroInteractions(schedulingService);
    }

    @Test
    public void shouldCloseEligibleCouple() throws Exception {
        when(allEligibleCouples.exists("CASE X")).thenReturn(true);

        ecService.closeEligibleCouple(new EligibleCoupleCloseRequest("CASE X", "ANM X"));

        verify(allEligibleCouples).close("CASE X");
        verify(actionService).closeEligibleCouple("CASE X", "ANM X");
    }

    @Test
    public void shouldNotCloseEligibleCoupleWhenECDoesNotExist() throws Exception {
        when(allEligibleCouples.exists("CASE X")).thenReturn(false);

        ecService.closeEligibleCouple(new EligibleCoupleCloseRequest("CASE X", "ANM X"));

        verify(allEligibleCouples, times(0)).close("CASE X");
        verifyZeroInteractions(actionService);
    }
}
