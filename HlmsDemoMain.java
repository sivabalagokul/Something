package hlms.main;

import hlms.dao.*;
import hlms.daoimpl.*;
import hlms.entity.*;
import hlms.dto.*;
import hlms.service.*;
import hlms.serviceimpl.*;
import hlms.util.DAOException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Single consolidated entry point combining the two previous demo classes:
 *   - HlmsFullDemoMain    (entity/DAO layer walkthrough)
 *   - HlmsServiceDemoMain (DTO/service layer walkthrough)
 *
 * Running this class executes both demo passes back-to-back, one right
 * after the other:
 *
 *   1. runEntityLayerDemo()  - talks directly to hlms.entity / hlms.dao /
 *      hlms.daoimpl classes. INSERTs one row per table (21 tables) in
 *      FK-safe order, does a findById -> update -> findAll check on each,
 *      then DELETEs everything it created in reverse order.
 *
 *   2. runServiceLayerDemo() - same idea, one layer higher. Only touches
 *      hlms.dto.*DTO objects and hlms.service.*Service interfaces; all
 *      DTO <-> Entity conversion happens inside the *ServiceImpl classes,
 *      which delegate to the same *DAOImpl classes underneath.
 *
 * Because each pass cleans up every row it inserts before returning, the
 * two passes can run one after another (or be run repeatedly) against the
 * same existing database without leaving demo data behind or colliding on
 * primary keys.
 *
 * Before running:
 *   - Point src/util/DBConnection.java at your existing PostgreSQL database.
 *   - Make sure the 21 tables already exist (this class does not create
 *     schema, only rows).
 *
 * Compile / run:
 *   javac -cp .:postgresql-42.7.x.jar -d out src/entity/*.java src/dao/*.java src/util/*.java src/daoimpl/*.java src/dto/*.java src/service/*.java src/serviceimpl/*.java src/main/HlmsDemoMain.java
 *   java  -cp out:postgresql-42.7.x.jar hlms.main.HlmsDemoMain
 */
public class HlmsDemoMain {

    public static void main(String[] args) {
        try {
            System.out.println("\n============================================================");
            System.out.println(" PART 1 / 2 : Entity / DAO layer demo");
            System.out.println("============================================================");
            runEntityLayerDemo();

            System.out.println("\n============================================================");
            System.out.println(" PART 2 / 2 : DTO / Service layer demo");
            System.out.println("============================================================");
            runServiceLayerDemo();
        } catch (DAOException e) {
            System.err.println("Run aborted: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runEntityLayerDemo() throws DAOException {

        // ---------- 1. AppUser (customer + officer) ----------
        AppUserDAO appUserDao = new AppUserDAOImpl();

        AppUser customer = new AppUser();
        customer.setEmail("demo.customer@hlms.local");
        customer.setName("Demo Customer");
        customer.setMobile("9000000001");
        customer.setPasswordHash("hash-customer");
        customer.setRole("CUSTOMER");
        customer.setActive(true);
        customer.setIdType("PAN");
        customer.setIdNumber("ABCDE1234F");
        customer.setPrefSms(true);
        customer.setPrefEmail(true);
        customer.setPrefInapp(true);
        customer.setCreatedAt(OffsetDateTime.now());
        customer = appUserDao.insert(customer);
        report("AppUser (customer)", customer);

        AppUser officer = new AppUser();
        officer.setEmail("demo.officer@hlms.local");
        officer.setName("Demo Officer");
        officer.setMobile("9000000002");
        officer.setPasswordHash("hash-officer");
        officer.setRole("BANK_OFFICER");
        officer.setActive(true);
        officer.setIdType("EMP_ID");
        officer.setIdNumber("EMP-001");
        officer.setPrefSms(false);
        officer.setPrefEmail(true);
        officer.setPrefInapp(true);
        officer.setCreatedAt(OffsetDateTime.now());
        officer = appUserDao.insert(officer);
        report("AppUser (officer)", officer);

        readUpdateCount(customer.getEmail(), "name", "Demo Customer Updated",
                (u, v) -> u.setName(v), appUserDao::findById, appUserDao::update, appUserDao::findAll);

        // ---------- 2. LoanProduct ----------
        LoanProductDAO productDao = new LoanProductDAOImpl();
        LoanProduct product = new LoanProduct();
        product.setProductId("PRD-HOME-001");
        product.setName("Standard Home Loan");
        product.setCategory("HOME");
        product.setInterestRate(new BigDecimal("8.50"));
        product.setMaxTenureYears(20);
        product.setMaxLtv("80");
        product.setDescription("Demo product created by HlmsFullDemoMain");
        product = productDao.insert(product);
        report("LoanProduct", product);
        readUpdateCount(product.getProductId(), null, null, null,
                productDao::findById, productDao::update, productDao::findAll);

        // ---------- 3. RolePermission (no FK) ----------
        RolePermissionDAO rolePermDao = new RolePermissionDAOImpl();
        RolePermission rolePerm = new RolePermission();
        rolePerm.setRole("BANK_OFFICER");
        rolePerm.setModule("LOAN_APPLICATION");
        rolePerm.setCanView(true);
        rolePerm.setCanAdd(true);
        rolePerm.setCanEdit(true);
        rolePerm.setCanDelete(false);
        rolePerm = rolePermDao.insert(rolePerm);
        report("RolePermission", rolePerm);
        RolePermission foundRolePerm = rolePermDao.findById(rolePerm.getRole(), rolePerm.getModule());
        System.out.println("Found: " + foundRolePerm);
        rolePermDao.update(foundRolePerm);
        System.out.println("Total RolePermission rows: " + rolePermDao.findAll().size());

        // ---------- 4. CustomerEmploymentProfile (1:1 on user_email) ----------
        CustomerEmploymentProfileDAO empDao = new CustomerEmploymentProfileDAOImpl();
        CustomerEmploymentProfile empProfile = new CustomerEmploymentProfile();
        empProfile.setUserEmail(customer.getEmail());
        empProfile.setUser(customer);
        empProfile.setEmploymentType("SALARIED");
        empProfile.setEmployer("Demo Employer Pvt Ltd");
        empProfile.setMonthlyIncome(new BigDecimal("75000.00"));
        empProfile.setOtherEmis(new BigDecimal("5000.00"));
        empProfile.setDesiredAmount(new BigDecimal("2500000.00"));
        empProfile.setPan("ABCDE1234F");
        empProfile = empDao.insert(empProfile);
        report("CustomerEmploymentProfile", empProfile);
        readUpdateCount(empProfile.getUserEmail(), null, null, null,
                empDao::findById, empDao::update, empDao::findAll);

        // ---------- 5. LoanDraft ----------
        LoanDraftDAO draftDao = new LoanDraftDAOImpl();
        LoanDraft draft = new LoanDraft();
        draft.setDraftId("DRAFT-0001");
        draft.setUser(customer);
        draft.setCurrentStep(1);
        draft.setSavedAt(OffsetDateTime.now());
        draft.setLoanType("HOME");
        draft.setLoanAmount(new BigDecimal("2500000.00"));
        draft.setTenureYears(20);
        draft.setFormData("{}");
        draft = draftDao.insert(draft);
        report("LoanDraft", draft);
        readUpdateCount(draft.getDraftId(), null, null, null,
                draftDao::findById, draftDao::update, draftDao::findAll);

        // ---------- 6. LoanApplication ----------
        LoanApplicationDAO appDao = new LoanApplicationDAOImpl();
        LoanApplication loanApp = new LoanApplication();
        loanApp.setAppId("APP-0001");
        loanApp.setTrackingNo("TRK-0001");
        loanApp.setUser(customer);
        loanApp.setProduct(product);
        loanApp.setLoanAmount(new BigDecimal("2500000.00"));
        loanApp.setTenureYears(20);
        loanApp.setPurpose("Purchase of flat");
        loanApp.setInterestRate(new BigDecimal("8.50"));
        loanApp.setStatus("SUBMITTED");
        loanApp.setStageIndex(1);
        loanApp.setCreatedAt(OffsetDateTime.now());
        loanApp.setApplicantName("Demo Customer");
        loanApp.setApplicantMobile("9000000001");
        loanApp.setApplicantPan("ABCDE1234F");
        loanApp.setAddrDoorNo("12A");
        loanApp.setAddrStreet("MG Road");
        loanApp.setAddrCity("Chennai");
        loanApp.setAddrState("Tamil Nadu");
        loanApp.setAddrPincode("600001");
        loanApp.setEmploymentType("SALARIED");
        loanApp.setEmployer("Demo Employer Pvt Ltd");
        loanApp.setMonthlyIncome(new BigDecimal("75000.00"));
        loanApp.setOtherEmis(new BigDecimal("5000.00"));
        loanApp.setPropertyAddress("Flat 4B, Green Towers, Chennai");
        loanApp.setPropertyType("APARTMENT");
        loanApp.setPropertyValue(new BigDecimal("3000000.00"));
        loanApp = appDao.insert(loanApp);
        report("LoanApplication", loanApp);
        readUpdateCount(loanApp.getAppId(), null, null, null,
                appDao::findById, appDao::update, appDao::findAll);

        // ---------- 7. ApplicationDocument ----------
        ApplicationDocumentDAO appDocDao = new ApplicationDocumentDAOImpl();
        ApplicationDocument appDoc = new ApplicationDocument();
        appDoc.setDocumentId("DOC-0001");
        appDoc.setApplication(loanApp);
        appDoc.setDocType("ID_PROOF");
        appDoc.setFileName("id-proof.pdf");
        appDoc.setFileSize(102400);
        appDoc.setUploadedAt(OffsetDateTime.now());
        appDoc.setVerificationStatus("PENDING");
        appDoc.setVerifiedBy(officer);
        appDoc.setVerifiedAt(OffsetDateTime.now());
        appDoc.setRemarks("Awaiting review");
        appDoc = appDocDao.insert(appDoc);
        report("ApplicationDocument", appDoc);
        readUpdateCount(appDoc.getDocumentId(), null, null, null,
                appDocDao::findById, appDocDao::update, appDocDao::findAll);

        // ---------- 8. LegalVerification (1:1 on app_id) ----------
        LegalVerificationDAO legalVerDao = new LegalVerificationDAOImpl();
        LegalVerification legalVer = new LegalVerification();
        legalVer.setAppId(loanApp.getAppId());
        legalVer.setApplication(loanApp);
        legalVer.setDecision("PENDING");
        legalVer.setDecisionDate(OffsetDateTime.now());
        legalVer.setOfficer(officer);
        legalVer.setRemarks("Initial legal review");
        legalVer = legalVerDao.insert(legalVer);
        report("LegalVerification", legalVer);
        readUpdateCount(legalVer.getAppId(), null, null, null,
                legalVerDao::findById, legalVerDao::update, legalVerDao::findAll);

        // ---------- 9. LegalChecklistItem ----------
        LegalChecklistItemDAO checklistDao = new LegalChecklistItemDAOImpl();
        LegalChecklistItem checklistItem = new LegalChecklistItem();
        checklistItem.setChecklistItemId("CHK-0001");
        checklistItem.setApplication(loanApp);
        checklistItem.setChecklistKey("TITLE_DEED");
        checklistItem.setStatus("PENDING");
        checklistItem = checklistDao.insert(checklistItem);
        report("LegalChecklistItem", checklistItem);
        readUpdateCount(checklistItem.getChecklistItemId(), null, null, null,
                checklistDao::findById, checklistDao::update, checklistDao::findAll);

        // ---------- 10. BankOfficeDecision (1:1 on app_id) ----------
        BankOfficeDecisionDAO decisionDao = new BankOfficeDecisionDAOImpl();
        BankOfficeDecision decision = new BankOfficeDecision();
        decision.setAppId(loanApp.getAppId());
        decision.setApplication(loanApp);
        decision.setDecision("APPROVED");
        decision.setOfficer(officer);
        decision.setDecisionDate(OffsetDateTime.now());
        decision.setReason("Meets eligibility criteria");
        decision.setRemarks("Approved with standard terms");
        decision = decisionDao.insert(decision);
        report("BankOfficeDecision", decision);
        readUpdateCount(decision.getAppId(), null, null, null,
                decisionDao::findById, decisionDao::update, decisionDao::findAll);

        // ---------- 11. Disbursement (1:1 on app_id) ----------
        DisbursementDAO disbDao = new DisbursementDAOImpl();
        Disbursement disbursement = new Disbursement();
        disbursement.setAppId(loanApp.getAppId());
        disbursement.setApplication(loanApp);
        disbursement.setDisbursedDate(OffsetDateTime.now());
        disbursement.setAmount(new BigDecimal("2500000.00"));
        disbursement.setRefNo("DISB-REF-0001");
        disbursement.setMode("NEFT");
        disbursement.setBankDetails("HDFC Bank, A/C ****1234");
        disbursement = disbDao.insert(disbursement);
        report("Disbursement", disbursement);
        readUpdateCount(disbursement.getAppId(), null, null, null,
                disbDao::findById, disbDao::update, disbDao::findAll);

        // ---------- 12. InsurancePolicy ----------
        InsurancePolicyDAO insDao = new InsurancePolicyDAOImpl();
        InsurancePolicy policy = new InsurancePolicy();
        policy.setPolicyId("POL-0001");
        policy.setApplication(loanApp);
        policy.setPolicyType("PROPERTY");
        policy.setStatus("ACTIVE");
        policy.setValidTill(LocalDate.now().plusYears(20));
        policy = insDao.insert(policy);
        report("InsurancePolicy", policy);
        readUpdateCount(policy.getPolicyId(), null, null, null,
                insDao::findById, insDao::update, insDao::findAll);

        // ---------- 13. EmiInstallment ----------
        EmiInstallmentDAO emiDao = new EmiInstallmentDAOImpl();
        EmiInstallment emi = new EmiInstallment();
        emi.setInstallmentId("EMI-0001");
        emi.setApplication(loanApp);
        emi.setInstallmentNo(1);
        emi.setDueDate(LocalDate.now().plusMonths(1));
        emi.setEmiAmount(new BigDecimal("21700.00"));
        emi.setPrincipalComponent(new BigDecimal("4033.00"));
        emi.setInterestComponent(new BigDecimal("17667.00"));
        emi.setClosingBalance(new BigDecimal("2495967.00"));
        emi.setStatus("DUE");
        emi.setPaidDate(null);
        emi = emiDao.insert(emi);
        report("EmiInstallment", emi);
        readUpdateCount(emi.getInstallmentId(), null, null, null,
                emiDao::findById, emiDao::update, emiDao::findAll);

        // ---------- 14. ServiceRequest ----------
        ServiceRequestDAO srDao = new ServiceRequestDAOImpl();
        ServiceRequest sr = new ServiceRequest();
        sr.setRequestId("SR-0001");
        sr.setRefNo("SR-REF-0001");
        sr.setApplication(loanApp);
        sr.setUser(customer);
        sr.setRequestType("STATEMENT_REQUEST");
        sr.setDescription("Requesting loan statement");
        sr.setStatus("OPEN");
        sr.setCreatedAt(OffsetDateTime.now());
        sr = srDao.insert(sr);
        report("ServiceRequest", sr);
        readUpdateCount(sr.getRequestId(), null, null, null,
                srDao::findById, srDao::update, srDao::findAll);

        // ---------- 15. PostDisbursementDocument ----------
        PostDisbursementDocumentDAO pdDocDao = new PostDisbursementDocumentDAOImpl();
        PostDisbursementDocument pdDoc = new PostDisbursementDocument();
        pdDoc.setPdDocId("PDDOC-0001");
        pdDoc.setApplication(loanApp);
        pdDoc.setDocType("INSURANCE_CERTIFICATE");
        pdDoc.setFileName("insurance-cert.pdf");
        pdDoc.setFileSize(51200);
        pdDoc.setNote("Uploaded after disbursement");
        pdDoc.setStatus("PENDING");
        pdDoc.setUploadedAt(OffsetDateTime.now());
        pdDoc.setVerifiedBy(officer);
        pdDoc.setVerifiedAt(OffsetDateTime.now());
        pdDoc.setRemarks("Awaiting review");
        pdDoc = pdDocDao.insert(pdDoc);
        report("PostDisbursementDocument", pdDoc);
        readUpdateCount(pdDoc.getPdDocId(), null, null, null,
                pdDocDao::findById, pdDocDao::update, pdDocDao::findAll);

        // ---------- 16. AuctionCase (1:1 on app_id) ----------
        AuctionCaseDAO auctionCaseDao = new AuctionCaseDAOImpl();
        AuctionCase auctionCase = new AuctionCase();
        auctionCase.setAppId(loanApp.getAppId());
        auctionCase.setApplication(loanApp);
        auctionCase.setStage("NOTICE_ISSUED");
        auctionCase.setNoticeDate(LocalDate.now());
        auctionCase.setNoticeDueDate(LocalDate.now().plusDays(60));
        auctionCase.setDemandAmount(new BigDecimal("2495967.00"));
        auctionCase.setPossessionDate(null);
        auctionCase.setAuctionDate(null);
        auctionCase.setReservePrice(new BigDecimal("2800000.00"));
        auctionCase = auctionCaseDao.insert(auctionCase);
        report("AuctionCase", auctionCase);
        readUpdateCount(auctionCase.getAppId(), null, null, null,
                auctionCaseDao::findById, auctionCaseDao::update, auctionCaseDao::findAll);

        // ---------- 17. AuctionNote ----------
        AuctionNoteDAO auctionNoteDao = new AuctionNoteDAOImpl();
        AuctionNote auctionNote = new AuctionNote();
        auctionNote.setNoteId("ANOTE-0001");
        auctionNote.setAuctionCase(auctionCase);
        auctionNote.setNoteDate(OffsetDateTime.now());
        auctionNote.setNoteText("Notice served to borrower");
        auctionNote = auctionNoteDao.insert(auctionNote);
        report("AuctionNote", auctionNote);
        readUpdateCount(auctionNote.getNoteId(), null, null, null,
                auctionNoteDao::findById, auctionNoteDao::update, auctionNoteDao::findAll);

        // ---------- 18. LegalNotice ----------
        LegalNoticeDAO noticeDao = new LegalNoticeDAOImpl();
        LegalNotice notice = new LegalNotice();
        notice.setNoticeId("NOTICE-0001");
        notice.setNoticeNo("NOTICE-NO-0001");
        notice.setApplication(loanApp);
        notice.setNoticeType("DEMAND_NOTICE");
        notice.setIssueDate(LocalDate.now());
        notice.setDueDate(LocalDate.now().plusDays(30));
        notice.setStatus("SENT");
        notice.setOfficer(officer);
        notice.setContent("Demand notice content");
        notice = noticeDao.insert(notice);
        report("LegalNotice", notice);
        readUpdateCount(notice.getNoticeId(), null, null, null,
                noticeDao::findById, noticeDao::update, noticeDao::findAll);

        // ---------- 19. Bid ----------
        BidDAO bidDao = new BidDAOImpl();
        Bid bid = new Bid();
        bid.setBidId("BID-0001");
        bid.setAuctionCase(auctionCase);
        bid.setBidder(customer);
        bid.setBidAmount(new BigDecimal("2850000.00"));
        bid.setCreatedAt(OffsetDateTime.now());
        bid = bidDao.insert(bid);
        report("Bid", bid);
        Bid foundBid = bidDao.findById(bid.getBidId());
        System.out.println("Found: " + foundBid);
        bidDao.update(foundBid);
        System.out.println("Total Bid rows: " + bidDao.findAll().size());

        // ---------- 20. Notification ----------
        NotificationDAO notifDao = new NotificationDAOImpl();
        Notification notification = new Notification();
        notification.setNotificationId("NOTIF-0001");
        notification.setUser(customer);
        notification.setTitle("Application submitted");
        notification.setBody("Your loan application has been submitted successfully.");
        notification.setChannels(new String[]{"EMAIL", "INAPP"});
        notification.setIsRead(false);
        notification.setCreatedAt(OffsetDateTime.now());
        notification = notifDao.insert(notification);
        report("Notification", notification);
        readUpdateCount(notification.getNotificationId(), null, null, null,
                notifDao::findById, notifDao::update, notifDao::findAll);

        // ---------- 21. AuditLog (BIGSERIAL id) ----------
        AuditLogDAO auditDao = new AuditLogDAOImpl();
        AuditLog audit = new AuditLog();
        audit.setEntityType("LOAN_APPLICATION");
        audit.setEntityId(loanApp.getAppId());
        audit.setAction("CREATE");
        audit.setActor(officer);
        audit.setBeforeData("{}");
        audit.setAfterData("{\"status\":\"SUBMITTED\"}");
        audit.setCreatedAt(OffsetDateTime.now());
        audit = auditDao.insert(audit);
        report("AuditLog", audit);
        AuditLog foundAudit = auditDao.findById(audit.getLogId());
        System.out.println("Found: " + foundAudit);
        auditDao.update(foundAudit);
        System.out.println("Total AuditLog rows: " + auditDao.findAll().size());

        System.out.println("\n=== All 21 inserts complete. Cleaning up in reverse order... ===\n");

        // ---------- Cleanup: delete everything in reverse dependency order ----------
        deleteQuietly("AuditLog", () -> auditDao.delete(audit.getLogId()));
        deleteQuietly("Notification", () -> notifDao.delete(notification.getNotificationId()));
        deleteQuietly("Bid", () -> bidDao.delete(bid.getBidId()));
        deleteQuietly("LegalNotice", () -> noticeDao.delete(notice.getNoticeId()));
        deleteQuietly("AuctionNote", () -> auctionNoteDao.delete(auctionNote.getNoteId()));
        deleteQuietly("AuctionCase", () -> auctionCaseDao.delete(auctionCase.getAppId()));
        deleteQuietly("PostDisbursementDocument", () -> pdDocDao.delete(pdDoc.getPdDocId()));
        deleteQuietly("ServiceRequest", () -> srDao.delete(sr.getRequestId()));
        deleteQuietly("EmiInstallment", () -> emiDao.delete(emi.getInstallmentId()));
        deleteQuietly("InsurancePolicy", () -> insDao.delete(policy.getPolicyId()));
        deleteQuietly("Disbursement", () -> disbDao.delete(disbursement.getAppId()));
        deleteQuietly("BankOfficeDecision", () -> decisionDao.delete(decision.getAppId()));
        deleteQuietly("LegalChecklistItem", () -> checklistDao.delete(checklistItem.getChecklistItemId()));
        deleteQuietly("LegalVerification", () -> legalVerDao.delete(legalVer.getAppId()));
        deleteQuietly("ApplicationDocument", () -> appDocDao.delete(appDoc.getDocumentId()));
        deleteQuietly("LoanApplication", () -> appDao.delete(loanApp.getAppId()));
        deleteQuietly("LoanDraft", () -> draftDao.delete(draft.getDraftId()));
        deleteQuietly("CustomerEmploymentProfile", () -> empDao.delete(empProfile.getUserEmail()));
        deleteQuietly("RolePermission", () -> rolePermDao.delete(rolePerm.getRole(), rolePerm.getModule()));
        deleteQuietly("LoanProduct", () -> productDao.delete(product.getProductId()));
        deleteQuietly("AppUser (officer)", () -> appUserDao.delete(officer.getEmail()));
        deleteQuietly("AppUser (customer)", () -> appUserDao.delete(customer.getEmail()));

        System.out.println("\n=== Done. ===");
    }

    private static void runServiceLayerDemo() throws DAOException {

        section("1. AppUser");
        AppUserService appUserService = new AppUserServiceImpl();

        AppUserDTO customer = new AppUserDTO();
        customer.setEmail("demo.customer@hlms.local");
        customer.setName("Demo Customer");
        customer.setMobile("9000000001");
        customer.setPasswordHash("hash-customer");
        customer.setRole("customer");
        customer.setActive(true);
        customer.setIdType("PAN");
        customer.setIdNumber("ABCDE1234F");
        customer.setPrefSms(true);
        customer.setPrefEmail(true);
        customer.setPrefInapp(true);
        customer.setCreatedAt(OffsetDateTime.now());
        customer = appUserService.create(customer);
        report("AppUser (customer)", customer);

        AppUserDTO officer = new AppUserDTO();
        officer.setEmail("demo.officer@hlms.local");
        officer.setName("Demo Officer");
        officer.setMobile("9000000002");
        officer.setPasswordHash("hash-officer");
        officer.setRole("bankoffice");
        officer.setActive(true);
        officer.setIdType("EMP_ID");
        officer.setIdNumber("EMP-001");
        officer.setPrefSms(false);
        officer.setPrefEmail(true);
        officer.setPrefInapp(true);
        officer.setCreatedAt(OffsetDateTime.now());
        officer = appUserService.create(officer);
        report("AppUser (officer)", officer);

        AppUserDTO bidder = new AppUserDTO();
        bidder.setEmail("demo.bidder@hlms.local");
        bidder.setName("Demo Bidder");
        bidder.setMobile("9000000003");
        bidder.setPasswordHash("hash-bidder");
        bidder.setRole("bidder");
        bidder.setActive(true);
        bidder.setIdType("PAN");
        bidder.setIdNumber("BIDDR5678K");
        bidder.setPrefSms(true);
        bidder.setPrefEmail(true);
        bidder.setPrefInapp(true);
        bidder.setCreatedAt(OffsetDateTime.now());
        bidder = appUserService.create(bidder);
        report("AppUser (bidder)", bidder);

        customer.setName("Demo Customer Updated");
        customer = appUserService.update(customer);
        report("AppUser (customer) after update", appUserService.findById(customer.getEmail()));
        System.out.println("AppUser count: " + appUserService.findAll().size());

        section("2. LoanProduct");
        LoanProductService loanProductService = new LoanProductServiceImpl();
        LoanProductDTO product = new LoanProductDTO();
        product.setProductId("DEMO-HP");
        product.setName("Demo Home Purchase Loan");
        product.setCategory("Home Purchase");
        product.setInterestRate(new BigDecimal("8.50"));
        product.setMaxTenureYears(30);
        product.setMaxLtv("90%");
        product.setDescription("Demo product created by the service-layer walkthrough.");
        product = loanProductService.create(product);
        report("LoanProduct", product);
        product.setDescription("Demo product (updated).");
        product = loanProductService.update(product);
        report("LoanProduct after update", loanProductService.findById(product.getProductId()));
        System.out.println("LoanProduct count: " + loanProductService.findAll().size());

        section("3. RolePermission");
        RolePermissionService rolePermissionService = new RolePermissionServiceImpl();
        RolePermissionDTO rolePermission = new RolePermissionDTO();
        rolePermission.setRole("customer");
        rolePermission.setModule("loan_application");
        rolePermission.setCanView(true);
        rolePermission.setCanAdd(true);
        rolePermission.setCanEdit(false);
        rolePermission.setCanDelete(false);
        rolePermission = rolePermissionService.create(rolePermission);
        report("RolePermission", rolePermission);
        rolePermission.setCanEdit(true);
        rolePermission = rolePermissionService.update(rolePermission);
        report("RolePermission after update", rolePermissionService.findById(rolePermission.getRole(), rolePermission.getModule()));
        System.out.println("RolePermission count: " + rolePermissionService.findAll().size());

        section("4. CustomerEmploymentProfile");
        CustomerEmploymentProfileService employmentProfileService = new CustomerEmploymentProfileServiceImpl();
        CustomerEmploymentProfileDTO employmentProfile = new CustomerEmploymentProfileDTO();
        employmentProfile.setUserEmail(customer.getEmail());
        employmentProfile.setEmploymentType("Salaried");
        employmentProfile.setEmployer("Demo Corp");
        employmentProfile.setMonthlyIncome(new BigDecimal("75000.00"));
        employmentProfile.setOtherEmis(new BigDecimal("5000.00"));
        employmentProfile.setDesiredAmount(new BigDecimal("2500000.00"));
        employmentProfile.setPan("ABCDE1234F");
        employmentProfile = employmentProfileService.create(employmentProfile);
        report("CustomerEmploymentProfile", employmentProfile);
        employmentProfile.setEmployer("Demo Corp (updated)");
        employmentProfile = employmentProfileService.update(employmentProfile);
        report("CustomerEmploymentProfile after update", employmentProfileService.findById(employmentProfile.getUserEmail()));
        System.out.println("CustomerEmploymentProfile count: " + employmentProfileService.findAll().size());

        section("5. LoanDraft");
        LoanDraftService loanDraftService = new LoanDraftServiceImpl();
        LoanDraftDTO loanDraft = new LoanDraftDTO();
        loanDraft.setDraftId("DEMO-DRAFT-001");
        loanDraft.setUserEmail(customer.getEmail());
        loanDraft.setCurrentStep(1);
        loanDraft.setSavedAt(OffsetDateTime.now());
        loanDraft.setLoanType("HP");
        loanDraft.setLoanAmount(new BigDecimal("2500000.00"));
        loanDraft.setTenureYears(20);
        loanDraft.setFormData("{}");
        loanDraft = loanDraftService.create(loanDraft);
        report("LoanDraft", loanDraft);
        loanDraft.setCurrentStep(2);
        loanDraft = loanDraftService.update(loanDraft);
        report("LoanDraft after update", loanDraftService.findById(loanDraft.getDraftId()));
        System.out.println("LoanDraft count: " + loanDraftService.findAll().size());

        section("6. LoanApplication");
        LoanApplicationService loanApplicationService = new LoanApplicationServiceImpl();
        LoanApplicationDTO application = new LoanApplicationDTO();
        application.setAppId("DEMO-APP-001");
        application.setTrackingNo("TRK-DEMO-001");
        application.setUserEmail(customer.getEmail());
        application.setProductId(product.getProductId());
        application.setLoanAmount(new BigDecimal("2500000.00"));
        application.setTenureYears(20);
        application.setPurpose("Home purchase");
        application.setInterestRate(new BigDecimal("8.50"));
        application.setStatus("Submitted");
        application.setStageIndex(0);
        application.setCreatedAt(OffsetDateTime.now());
        application.setApplicantName("Demo Customer");
        application.setApplicantMobile("9000000001");
        application.setApplicantPan("ABCDE1234F");
        application.setAddrDoorNo("12");
        application.setAddrStreet("Demo Street");
        application.setAddrCity("Chennai");
        application.setAddrState("Tamil Nadu");
        application.setAddrPincode("600001");
        application.setEmploymentType("Salaried");
        application.setEmployer("Demo Corp");
        application.setMonthlyIncome(new BigDecimal("75000.00"));
        application.setOtherEmis(new BigDecimal("5000.00"));
        application.setPropertyAddress("Demo Property Address");
        application.setPropertyType("Apartment");
        application.setPropertyValue(new BigDecimal("3000000.00"));
        application = loanApplicationService.create(application);
        report("LoanApplication", application);
        application.setStatus("Under Review");
        application = loanApplicationService.update(application);
        report("LoanApplication after update", loanApplicationService.findById(application.getAppId()));
        System.out.println("LoanApplication count: " + loanApplicationService.findAll().size());

        section("7. ApplicationDocument");
        ApplicationDocumentService applicationDocumentService = new ApplicationDocumentServiceImpl();
        ApplicationDocumentDTO document = new ApplicationDocumentDTO();
        document.setDocumentId("DEMO-DOC-001");
        document.setAppId(application.getAppId());
        document.setDocType("idProof");
        document.setFileName("id-proof.pdf");
        document.setFileSize(102400);
        document.setUploadedAt(OffsetDateTime.now());
        document.setVerificationStatus("Pending");
        document.setVerifiedByEmail(null);
        document.setVerifiedAt(null);
        document.setRemarks("Awaiting review");
        document = applicationDocumentService.create(document);
        report("ApplicationDocument", document);
        document.setVerificationStatus("Verified");
        document.setVerifiedByEmail(officer.getEmail());
        document.setVerifiedAt(OffsetDateTime.now());
        document = applicationDocumentService.update(document);
        report("ApplicationDocument after update", applicationDocumentService.findById(document.getDocumentId()));
        System.out.println("ApplicationDocument count: " + applicationDocumentService.findAll().size());

        section("8. LegalVerification");
        LegalVerificationService legalVerificationService = new LegalVerificationServiceImpl();
        LegalVerificationDTO legalVerification = new LegalVerificationDTO();
        legalVerification.setAppId(application.getAppId());
        legalVerification.setDecision("Query Raised");
        legalVerification.setDecisionDate(OffsetDateTime.now());
        legalVerification.setOfficerEmail(officer.getEmail());
        legalVerification.setRemarks("Needs sale deed copy");
        legalVerification = legalVerificationService.create(legalVerification);
        report("LegalVerification", legalVerification);
        legalVerification.setDecision("Approved");
        legalVerification = legalVerificationService.update(legalVerification);
        report("LegalVerification after update", legalVerificationService.findById(legalVerification.getAppId()));
        System.out.println("LegalVerification count: " + legalVerificationService.findAll().size());

        section("9. LegalChecklistItem");
        LegalChecklistItemService checklistItemService = new LegalChecklistItemServiceImpl();
        LegalChecklistItemDTO checklistItem = new LegalChecklistItemDTO();
        checklistItem.setChecklistItemId("DEMO-CHK-001");
        checklistItem.setAppId(application.getAppId());
        checklistItem.setChecklistKey("titleDeed");
        checklistItem.setStatus("Pending");
        checklistItem = checklistItemService.create(checklistItem);
        report("LegalChecklistItem", checklistItem);
        checklistItem.setStatus("Verified");
        checklistItem = checklistItemService.update(checklistItem);
        report("LegalChecklistItem after update", checklistItemService.findById(checklistItem.getChecklistItemId()));
        System.out.println("LegalChecklistItem count: " + checklistItemService.findAll().size());

        section("10. BankOfficeDecision");
        BankOfficeDecisionService bankOfficeDecisionService = new BankOfficeDecisionServiceImpl();
        BankOfficeDecisionDTO bankDecision = new BankOfficeDecisionDTO();
        bankDecision.setAppId(application.getAppId());
        bankDecision.setDecision("Approved");
        bankDecision.setOfficerEmail(officer.getEmail());
        bankDecision.setDecisionDate(OffsetDateTime.now());
        bankDecision.setReason("Meets eligibility criteria");
        bankDecision.setRemarks("Proceed to disbursement");
        bankDecision = bankOfficeDecisionService.create(bankDecision);
        report("BankOfficeDecision", bankDecision);
        bankDecision.setRemarks("Proceed to disbursement (confirmed)");
        bankDecision = bankOfficeDecisionService.update(bankDecision);
        report("BankOfficeDecision after update", bankOfficeDecisionService.findById(bankDecision.getAppId()));
        System.out.println("BankOfficeDecision count: " + bankOfficeDecisionService.findAll().size());

        section("11. Disbursement");
        DisbursementService disbursementService = new DisbursementServiceImpl();
        DisbursementDTO disbursement = new DisbursementDTO();
        disbursement.setAppId(application.getAppId());
        disbursement.setDisbursedDate(OffsetDateTime.now());
        disbursement.setAmount(new BigDecimal("2500000.00"));
        disbursement.setRefNo("REF-DEMO-001");
        disbursement.setMode("NEFT");
        disbursement.setBankDetails("HDFC Bank - 1234");
        disbursement = disbursementService.create(disbursement);
        report("Disbursement", disbursement);
        disbursement.setMode("RTGS");
        disbursement = disbursementService.update(disbursement);
        report("Disbursement after update", disbursementService.findById(disbursement.getAppId()));
        System.out.println("Disbursement count: " + disbursementService.findAll().size());

        section("12. InsurancePolicy");
        InsurancePolicyService insurancePolicyService = new InsurancePolicyServiceImpl();
        InsurancePolicyDTO policy = new InsurancePolicyDTO();
        policy.setPolicyId("DEMO-POL-001");
        policy.setAppId(application.getAppId());
        policy.setPolicyType("property");
        policy.setStatus("Active");
        policy.setValidTill(LocalDate.now().plusYears(1));
        policy = insurancePolicyService.create(policy);
        report("InsurancePolicy", policy);
        policy.setStatus("Renewed");
        policy = insurancePolicyService.update(policy);
        report("InsurancePolicy after update", insurancePolicyService.findById(policy.getPolicyId()));
        System.out.println("InsurancePolicy count: " + insurancePolicyService.findAll().size());

        section("13. EmiInstallment");
        EmiInstallmentService emiInstallmentService = new EmiInstallmentServiceImpl();
        EmiInstallmentDTO installment = new EmiInstallmentDTO();
        installment.setInstallmentId("DEMO-EMI-001");
        installment.setAppId(application.getAppId());
        installment.setInstallmentNo(1);
        installment.setDueDate(LocalDate.now().plusMonths(1));
        installment.setEmiAmount(new BigDecimal("21500.00"));
        installment.setPrincipalComponent(new BigDecimal("15000.00"));
        installment.setInterestComponent(new BigDecimal("6500.00"));
        installment.setClosingBalance(new BigDecimal("2485000.00"));
        installment.setStatus("Due");
        installment.setPaidDate(null);
        installment = emiInstallmentService.create(installment);
        report("EmiInstallment", installment);
        installment.setStatus("Paid");
        installment.setPaidDate(LocalDate.now());
        installment = emiInstallmentService.update(installment);
        report("EmiInstallment after update", emiInstallmentService.findById(installment.getInstallmentId()));
        System.out.println("EmiInstallment count: " + emiInstallmentService.findAll().size());

        section("14. ServiceRequest");
        ServiceRequestService serviceRequestService = new ServiceRequestServiceImpl();
        ServiceRequestDTO serviceRequest = new ServiceRequestDTO();
        serviceRequest.setRequestId("DEMO-SR-001");
        serviceRequest.setRefNo("SR-REF-001");
        serviceRequest.setAppId(application.getAppId());
        serviceRequest.setUserEmail(customer.getEmail());
        serviceRequest.setRequestType("Address Change");
        serviceRequest.setDescription("Update mailing address");
        serviceRequest.setStatus("Requested");
        serviceRequest.setCreatedAt(OffsetDateTime.now());
        serviceRequest = serviceRequestService.create(serviceRequest);
        report("ServiceRequest", serviceRequest);
        serviceRequest.setStatus("Resolved");
        serviceRequest = serviceRequestService.update(serviceRequest);
        report("ServiceRequest after update", serviceRequestService.findById(serviceRequest.getRequestId()));
        System.out.println("ServiceRequest count: " + serviceRequestService.findAll().size());

        section("15. PostDisbursementDocument");
        PostDisbursementDocumentService pdDocService = new PostDisbursementDocumentServiceImpl();
        PostDisbursementDocumentDTO pdDoc = new PostDisbursementDocumentDTO();
        pdDoc.setPdDocId("DEMO-PDD-001");
        pdDoc.setAppId(application.getAppId());
        pdDoc.setDocType("Property Tax Receipt");
        pdDoc.setFileName("tax-receipt.pdf");
        pdDoc.setFileSize(51200);
        pdDoc.setNote("FY 2025-26 receipt");
        pdDoc.setStatus("Pending");
        pdDoc.setUploadedAt(OffsetDateTime.now());
        pdDoc.setVerifiedByEmail(null);
        pdDoc.setVerifiedAt(null);
        pdDoc.setRemarks("Awaiting review");
        pdDoc = pdDocService.create(pdDoc);
        report("PostDisbursementDocument", pdDoc);
        pdDoc.setStatus("Verified");
        pdDoc.setVerifiedByEmail(officer.getEmail());
        pdDoc.setVerifiedAt(OffsetDateTime.now());
        pdDoc = pdDocService.update(pdDoc);
        report("PostDisbursementDocument after update", pdDocService.findById(pdDoc.getPdDocId()));
        System.out.println("PostDisbursementDocument count: " + pdDocService.findAll().size());

        section("16. AuctionCase");
        AuctionCaseService auctionCaseService = new AuctionCaseServiceImpl();
        AuctionCaseDTO auctionCase = new AuctionCaseDTO();
        auctionCase.setAppId(application.getAppId());
        auctionCase.setStage("Notice Issued");
        auctionCase.setNoticeDate(LocalDate.now());
        auctionCase.setNoticeDueDate(LocalDate.now().plusDays(60));
        auctionCase.setDemandAmount(new BigDecimal("2450000.00"));
        auctionCase.setPossessionDate(null);
        auctionCase.setAuctionDate(null);
        auctionCase.setReservePrice(new BigDecimal("2800000.00"));
        auctionCase = auctionCaseService.create(auctionCase);
        report("AuctionCase", auctionCase);
        auctionCase.setStage("Auction Scheduled");
        auctionCase.setAuctionDate(LocalDate.now().plusDays(90));
        auctionCase = auctionCaseService.update(auctionCase);
        report("AuctionCase after update", auctionCaseService.findById(auctionCase.getAppId()));
        System.out.println("AuctionCase count: " + auctionCaseService.findAll().size());

        section("17. AuctionNote");
        AuctionNoteService auctionNoteService = new AuctionNoteServiceImpl();
        AuctionNoteDTO auctionNote = new AuctionNoteDTO();
        auctionNote.setNoteId("DEMO-NOTE-001");
        auctionNote.setAppId(auctionCase.getAppId());
        auctionNote.setNoteDate(OffsetDateTime.now());
        auctionNote.setNoteText("Possession notice served to occupants.");
        auctionNote = auctionNoteService.create(auctionNote);
        report("AuctionNote", auctionNote);
        auctionNote.setNoteText("Possession notice served to occupants (follow-up added).");
        auctionNote = auctionNoteService.update(auctionNote);
        report("AuctionNote after update", auctionNoteService.findById(auctionNote.getNoteId()));
        System.out.println("AuctionNote count: " + auctionNoteService.findAll().size());

        section("18. LegalNotice");
        LegalNoticeService legalNoticeService = new LegalNoticeServiceImpl();
        LegalNoticeDTO legalNotice = new LegalNoticeDTO();
        legalNotice.setNoticeId("DEMO-LN-001");
        legalNotice.setNoticeNo("NOTICE-DEMO-001");
        legalNotice.setAppId(application.getAppId());
        legalNotice.setNoticeType("SARFAESI 13(2)");
        legalNotice.setIssueDate(LocalDate.now());
        legalNotice.setDueDate(LocalDate.now().plusDays(60));
        legalNotice.setStatus("Active");
        legalNotice.setOfficerEmail(officer.getEmail());
        legalNotice.setContent("Demand notice under Section 13(2) of the SARFAESI Act.");
        legalNotice = legalNoticeService.create(legalNotice);
        report("LegalNotice", legalNotice);
        legalNotice.setStatus("Closed");
        legalNotice = legalNoticeService.update(legalNotice);
        report("LegalNotice after update", legalNoticeService.findById(legalNotice.getNoticeId()));
        System.out.println("LegalNotice count: " + legalNoticeService.findAll().size());

        section("19. Bid");
        BidService bidService = new BidServiceImpl();
        BidDTO bid = new BidDTO();
        bid.setBidId("DEMO-BID-001");
        bid.setAppId(auctionCase.getAppId());
        bid.setBidderEmail(bidder.getEmail());
        bid.setBidAmount(new BigDecimal("2850000.00"));
        bid.setCreatedAt(OffsetDateTime.now());
        bid = bidService.create(bid);
        report("Bid", bid);
        bid.setBidAmount(new BigDecimal("2900000.00"));
        bid = bidService.update(bid);
        report("Bid after update", bidService.findById(bid.getBidId()));
        System.out.println("Bid count: " + bidService.findAll().size());

        section("20. Notification");
        NotificationService notificationService = new NotificationServiceImpl();
        NotificationDTO notification = new NotificationDTO();
        notification.setNotificationId("DEMO-NOTIF-001");
        notification.setUserEmail(customer.getEmail());
        notification.setTitle("Application submitted");
        notification.setBody("Your loan application DEMO-APP-001 has been submitted.");
        notification.setChannels(new String[]{"inapp", "email"});
        notification.setIsRead(false);
        notification.setCreatedAt(OffsetDateTime.now());
        notification = notificationService.create(notification);
        report("Notification", notification);
        notification.setIsRead(true);
        notification = notificationService.update(notification);
        report("Notification after update", notificationService.findById(notification.getNotificationId()));
        System.out.println("Notification count: " + notificationService.findAll().size());

        section("21. AuditLog");
        AuditLogService auditLogService = new AuditLogServiceImpl();
        AuditLogDTO auditLog = new AuditLogDTO();
        auditLog.setEntityType("LoanApplication");
        auditLog.setEntityId(application.getAppId());
        auditLog.setAction("CREATE");
        auditLog.setActorEmail(customer.getEmail());
        auditLog.setBeforeData(null);
        auditLog.setAfterData("{\"status\":\"Submitted\"}");
        auditLog.setCreatedAt(OffsetDateTime.now());
        auditLog = auditLogService.create(auditLog);
        report("AuditLog", auditLog);
        auditLog.setAction("UPDATE");
        auditLog = auditLogService.update(auditLog);
        report("AuditLog after update", auditLogService.findById(auditLog.getLogId()));
        System.out.println("AuditLog count: " + auditLogService.findAll().size());

        section("Cleanup (reverse order delete)");
        auditLogService.delete(auditLog.getLogId());
        notificationService.delete(notification.getNotificationId());
        bidService.delete(bid.getBidId());
        legalNoticeService.delete(legalNotice.getNoticeId());
        auctionNoteService.delete(auctionNote.getNoteId());
        auctionCaseService.delete(auctionCase.getAppId());
        pdDocService.delete(pdDoc.getPdDocId());
        serviceRequestService.delete(serviceRequest.getRequestId());
        emiInstallmentService.delete(installment.getInstallmentId());
        insurancePolicyService.delete(policy.getPolicyId());
        disbursementService.delete(disbursement.getAppId());
        bankOfficeDecisionService.delete(bankDecision.getAppId());
        checklistItemService.delete(checklistItem.getChecklistItemId());
        legalVerificationService.delete(legalVerification.getAppId());
        applicationDocumentService.delete(document.getDocumentId());
        loanApplicationService.delete(application.getAppId());
        loanDraftService.delete(loanDraft.getDraftId());
        employmentProfileService.delete(employmentProfile.getUserEmail());
        rolePermissionService.delete(rolePermission.getRole(), rolePermission.getModule());
        loanProductService.delete(product.getProductId());
        appUserService.delete(bidder.getEmail());
        appUserService.delete(officer.getEmail());
        appUserService.delete(customer.getEmail());
        System.out.println("All demo rows deleted.");
        System.out.println("\nService-layer demo run completed successfully.");
    }

    // ---------- small helpers shared by both demo methods above ----------

    private static void report(String label, Object entity) {
        System.out.println("Inserted " + label + ": " + entity);
    }

    private static void section(String title) {
        System.out.println("\n---------- " + title + " ----------");
    }

    @FunctionalInterface
    private interface Deleter {
        boolean delete() throws DAOException;
    }

    private static void deleteQuietly(String label, Deleter deleter) {
        try {
            boolean deleted = deleter.delete();
            System.out.println("Deleted " + label + ": " + deleted);
        } catch (DAOException e) {
            System.err.println("Could not delete " + label + ": " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface FieldSetter<T> {
        void set(T entity, String value);
    }

    @FunctionalInterface
    private interface FindById<T> {
        T find(String id) throws DAOException;
    }

    @FunctionalInterface
    private interface Update<T> {
        T update(T entity) throws DAOException;
    }

    @FunctionalInterface
    private interface FindAll<T> {
        List<T> find() throws DAOException;
    }

    /**
     * Generic findById -> (optional field tweak) -> update -> findAll/count,
     * mirroring the READ / UPDATE / READ-ALL steps every original *Main
     * class performed. Pass null for fieldName/newValue/setter to skip the
     * field tweak (e.g. for entities where updating is a no-op demo).
     */
    private static <T> void readUpdateCount(
            String id,
            String fieldName,
            String newValue,
            FieldSetter<T> setter,
            FindById<T> findById,
            Update<T> update,
            FindAll<T> findAll) throws DAOException {

        T found = findById.find(id);
        System.out.println("Found: " + found);
        if (found != null) {
            if (setter != null && newValue != null) {
                setter.set(found, newValue);
            }
            update.update(found);
            System.out.println("Updated: " + found);
        }
        System.out.println("Total rows: " + findAll.find().size());
    }
}
