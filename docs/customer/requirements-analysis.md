# Customer requirements analysis

Source: [customer-requirements.pdf](customer-requirements.pdf), physical PDF page 1 of 1, titled **Hệ thống quản lý đề tài sinh viên**. The complete page was extracted and visually inspected. Analysis date: 2026-09-08. No additional source or external reference is used.

Classification used throughout:

- **Explicit (E):** directly stated in the PDF; English descriptions are paraphrases of the Vietnamese source.
- **Inferred (I):** a consequence or interpretation of an explicit statement, requiring confirmation before treating it as additional scope.
- **Assumption (A):** a working interpretation used only to organize this analysis, not an approved requirement.
- **Open question (Q):** an unresolved customer decision; no behavior is prescribed.

Requirement IDs `REQ-01` through `REQ-32` identify explicit requirements. Inferences use `INF` IDs, assumptions use `A` IDs, and customer questions use `Q` IDs. Every source reference below refers to physical PDF page 1. The document is an analysis, not an implementation plan or an approved specification.

## 1. Project overview

**Explicit:** The system manages student topics/projects within the Faculty of Information Technology (khoa CNTT), covering course projects, scientific research (NCKH), specialized essays/projects (TLCN), and graduation theses (KLTN). It aims to automate the lifecycle from registration-period creation through lecturer/student registration, topic approval, supervisor and reviewer assignment, grading, and faculty publication of results (REQ-01).

Terminology: `GV` means lecturer; `SV` means student; `GVHD` means supervisor; `GVPB` means reviewer. “Faculty” refers to khoa; “department” refers to bộ môn. These English terms preserve the distinction in the source.

**Explicit:** Registration has two separate stages: preparing/publishing a topic list, then student groups registering for a published topic (REQ-02). Following approval, groups perform the work under assigned supervision, submit a report through the group leader, undergo review, and receive published results.

**Open:** The exact lifecycle states and variations among the four project types are not defined (Q-03, Q-09, Q-15).

## 2. Business objectives

| Classification | Objective | Evidence |
|---|---|---|
| E | Automate the complete faculty topic-management process. | REQ-01; p. 1, opening paragraph |
| E | Provide timely information through official school and faculty announcements. | REQ-30; p. 1, final paragraph |
| I | Coordinate registration, supervision, review, and publication through a shared process. | INF-01, derived from REQ-01–03 and REQ-26 |
| I | Apply the stated group, supervision, and grading restrictions consistently. | INF-02, derived from REQ-10, REQ-12–15, REQ-20, REQ-25 |

No measurable business targets, adoption targets, cost savings, delivery date, or success metrics are stated.

## 3. Actors / user roles

These are business roles, not a finalized account-role hierarchy. Role combinations and delegated authority are unspecified.

| Actor | Classification | Stated responsibilities or involvement |
|---|---|---|
| Faculty head (Trưởng khoa) | E | Creates registration periods and supplies their configuration (REQ-04–07). |
| Lecturer (GV) | E | Registers topics; may supervise or grade multiple topics subject to restrictions (REQ-08, REQ-10, REQ-24–25). |
| Supervisor (GVHD) | E | Assigned to guide a group's work; one or two lecturers supervise each topic (REQ-10–11, REQ-16). |
| Reviewer (GVPB) | E | Assigned for review; reviewer score-submission deadline is configured for TLCN/KLTN (REQ-06, REQ-11). Precise review powers are unspecified. |
| Student (SV) | E | Participates in a group and can view scores/evaluation results for their own topic (REQ-12–16, REQ-27). |
| Group leader | E | One member of each group; exclusively permitted to submit the report (REQ-12, REQ-17). Other group-management powers are unspecified. |
| Review council member | E | Lecturer participating in council evaluation and scoring (REQ-20–22). |
| Council chair | E | Consolidates members' evaluations and scores to determine the final review result (REQ-21–22). |
| Council secretary | E | Required council position; operational duties are not described (REQ-21). |
| Faculty of IT | E, organizational actor | Publishes results and receives reviewer scores (REQ-01, REQ-06, REQ-26). The individual account acting for it is unspecified. |
| School and faculty | E, organizational sources | Sources of official announcements (REQ-30); this does not establish separate school/faculty login roles. |
| Account manager / announcement publisher | I | Someone must exercise REQ-28 and REQ-30; an administrator role is not explicitly named (INF-03; Q-18). |

## 4. Functional requirements

All entries in this section are **Explicit**. The traceability table provides an individual row for each ID.

| Module | Required capabilities | IDs |
|---|---|---|
| Lifecycle and registration periods | Support the four project types and overall lifecycle; manage two registration stages; allow registration during prescribed times; let the faculty head create named, typed periods with lecturer/student start/end times and type-dependent dates. | REQ-01–07 |
| Topic catalogue and assignments | Support lecturer topic registration, topic approval, department-organized topics with clear content and one or two supervisors, and supervisor/reviewer assignment. | REQ-08–11 |
| Student groups and registration | Support group participation with at most three members and one leader, one group per student during execution, and one published topic per group; work begins following registration and acceptance under assigned supervision. | REQ-12–16 |
| Report submission | Permit only the group leader to submit the project report. | REQ-17 |
| Review and councils | Require review according to plan after completion; manage council formation with three to five lecturers, a chair and a secretary; enable chair consolidation of evaluations/scores. | REQ-18–22 |
| Grading | Calculate the final topic score as the arithmetic mean of component scores; allow lecturers to grade multiple topics but prohibit grading their own supervised topics. | REQ-23–25 |
| Results | Publish evaluation results after review and grading finish; allow students to view their topic's scores and evaluation results. | REQ-26–27 |
| Accounts, announcements, authorization | Manage user accounts, provide authorization and data-security solutions, and post official school/faculty announcements. | REQ-28–32 |

**Inferred:** INF-04: Recording membership, leadership, supervision, and grading relationships is necessary to evaluate the stated restrictions. This does not prescribe a database design, history feature, or administrative interface.

**Open:** “Manage” does not establish every create/read/update/delete operation. Deletion, reassignment, cancellation, self-registration, search, bulk import, and audit history are not specified.

## 5. Non-functional requirements

| Classification | Requirement or gap | Reference |
|---|---|---|
| E | Provide data-security solutions. The source gives no particular controls or measurable standard. | REQ-32 |
| E | Provide authorization solutions. Specific mechanisms and complete access policies are absent. | REQ-31 |
| E, qualitative objective | Announcements should provide information in a timely manner; no delivery-time guarantee is stated. | REQ-30 |
| Q | Performance, concurrent users, availability, backup/recovery, retention, accessibility, supported devices/browsers, language support, hosting, and security acceptance thresholds are unspecified. | Q-20 |

The PDF does not mandate a technology stack, architecture, deployment model, encryption method, SSO, or quantitative service-level targets. Registration windows are business timing rules, not response-time requirements.

## 6. Business rules

All rules below are **Explicit**, with unresolved details identified separately.

| Rule | Requirement | Unresolved detail |
|---|---|---|
| Registration follows two separate stages; group selection uses the published list. | REQ-02, REQ-15 | Stage transitions and calendar overlap (Q-03). |
| Registration is allowed according to prescribed times. | REQ-03 | Boundary inclusivity, timezone, late requests (Q-04). |
| Reviewer score deadline is configured only for TLCN/KLTN. | REQ-06 | Whether mandatory for both types and consequences of lateness (Q-02, Q-13). |
| Council report date is configured only for KLTN. | REQ-07 | Whether mandatory and review scheduling for other types (Q-02, Q-15). |
| Every topic belongs to a specific department and has clear content. | REQ-09 | Required content fields and topic reuse (Q-05). |
| Each topic has at least one and at most two supervisors. | REQ-10 | When this must hold in the lifecycle (Q-06). |
| Students work in groups of at most three, including one leader. | REQ-12 | Minimum size and membership changes (Q-07). |
| A student participates in only one group throughout project execution. | REQ-13 | Scope across periods/project types (Q-08). |
| A group registers for only one topic from the published list. | REQ-14–15 | Topic capacity across groups and replacement after rejection (Q-09–10). |
| Work follows registration and acceptance, with assigned guidance. | REQ-16 | Acceptance authority and distinction from topic approval (Q-01). |
| Only the group leader submits the project report. | REQ-17 | Resubmission, format, deadlines, and leader absence (Q-11). |
| Groups participate in review according to plan after completion. | REQ-18 | Applicable review format by project type (Q-15). |
| Councils comprise three to five lecturers, including one chair and one secretary. | REQ-20–21 | Whether chair/secretary must be different people and grading duties (Q-12). |
| Chair consolidates member evaluations and scores into the final review result. | REQ-22 | Workflow and approval/finalization mechanics (Q-14). |
| Final topic score is the arithmetic mean of component scores. | REQ-23 | Components, scale, rounding, and missing scores (Q-13). |
| A lecturer may grade multiple topics but cannot grade one they supervise. | REQ-24–25 | Non-grading council participation by a supervisor (Q-12). |
| Evaluation results are published after review and grading finish. | REQ-26 | Completion criteria and publication granularity (Q-16). |

## 7. Permissions and authorization rules

| Action | Explicit authority/restriction | Interpretation or open issue |
|---|---|---|
| Create registration period | Faculty head (REQ-04). | Other creators, delegates, edits, and deletion are not stated (Q-01). |
| Register lecturer topic | Lecturer participation (REQ-08), within configured timing (REQ-03). | Whether students can propose topics is not established (Q-05). |
| Approve topic / accept group registration | Both approval and acceptance appear (REQ-08, REQ-16). | Responsible roles and whether these are distinct decisions are open (Q-01). |
| Assign supervisors/reviewers; form councils | System capabilities are stated (REQ-11, REQ-19). | Authorized operators and department scope are open (Q-01). |
| Register group topic | Student groups register a published topic (REQ-14–15). | Leader-only registration is not stated (Q-07). |
| Submit report | Only the group leader (REQ-17). | This exclusivity is explicit; delegation is not granted (Q-11). |
| Grade topic | Lecturer must not supervise that topic (REQ-25). | Assignment-based access and score-edit rights remain open (Q-13, Q-18). |
| Consolidate council result | Council chair (REQ-22). | Secretary powers are unspecified (Q-12). |
| Publish results | Faculty publishes after review/grading completion (REQ-01, REQ-26). | Specific role and release workflow are open (Q-16). |
| View results | Students can view scores/results for their own topic (REQ-27). | **I, INF-05:** restricting students to their own topic is a plausible privacy interpretation, but the PDF does not expressly forbid viewing other topics (Q-17). |
| Manage accounts / post announcements | Capabilities stated (REQ-28, REQ-30). | No explicit administrator or publisher role (Q-18). |

**Inferred (INF-06):** The system needs a way to identify the acting user and their relevant role/relationships to enforce these rules. A login method, password workflow, session policy, or identity provider is not specified.

## 8. External systems / integrations

**Explicit:** No external software system, API, or integration is named.

The school and faculty are organizational actors/sources of announcements. Reviewer scores being submitted to the faculty does not itself imply a connection to an external grading system.

**Open (Q-19):** Confirm whether any existing identity, student-information, grading, or announcement system must exchange data with this system. No integration is assumed in scope.

## 9. Data/entities mentioned

This is a conceptual inventory, not a proposed schema. All rows describe **Explicit** concepts; unknown fields are not supplied.

| Entity/concept | Attributes or relationships mentioned | Requirement references |
|---|---|---|
| Registration period | Name; type: course/NCKH/TLCN/KLTN; lecturer registration start/end; student registration start/end; conditional reviewer score deadline; conditional council report date. | REQ-04–07 |
| Registration stage | Topic-list preparation/publication stage and group topic-selection stage. | REQ-02 |
| Faculty and department | IT faculty manages the process; each topic belongs to a specific department. | REQ-01, REQ-09 |
| Topic/project | Clear content, department, one or two supervisors; selected from a published list by a group. A separate title field is not specified. | REQ-09–10, REQ-14–15 |
| User account | Account management is required; account attributes are unspecified. | REQ-28 |
| Lecturer and student | Lecturer supervision/review/grading roles; student group membership. | REQ-08, REQ-10–13, REQ-24–25 |
| Student group / membership | At most three members, one leader, one topic; student belongs to one group during execution. | REQ-12–15 |
| Registration / approval | Lecturer and group registration, topic approval, and acceptance before work. Exact records and statuses are unspecified. | REQ-08, REQ-14–16 |
| Supervisor / reviewer assignment | Assigned lecturers guide or review topics. | REQ-10–11, REQ-16 |
| Project report | Submitted by group leader; fields and file constraints unspecified. | REQ-17 |
| Review plan / council | Planned review; council of three to five lecturers including chair and secretary. | REQ-18–21 |
| Evaluation / component score / final result | Member evaluations/scores, chair consolidation, arithmetic mean of components, published results. | REQ-22–27 |
| Official announcement | School/faculty information posted for users; content fields and audience controls unspecified. | REQ-30 |
| Authorization | Permissions are required; storage model is unspecified. | REQ-31 |

**Open:** IDs, mandatory fields beyond those listed, lifecycle history, ownership scopes, retention, and entity deletion behavior need confirmation where relevant (Q-05, Q-18, Q-20).

## 10. Reports or exports

- **Explicit:** Student project reports are submitted by group leaders (REQ-17). These are academic deliverables, not a stated requirement for generated management reports.
- **Explicit:** A topic list is published and evaluation results/scores are made available in the system (REQ-02, REQ-26–27).
- **Explicit:** The chair consolidates council evaluations and scores (REQ-22); a downloadable council report is not specified.
- **Open (Q-21):** No PDF/Excel/CSV export, printing, statistics dashboard, transcript, or administrative report is requested. Confirm whether any such output is required and, if so, its fields and audience.

## 11. Notifications

**Explicit (REQ-30):** Support posting official school and faculty announcements to provide timely information to users.

**Open (Q-22):** No email, SMS, push delivery, automatic deadline reminder, registration-status alert, grade-release alert, read receipt, or delivery schedule is stated. Confirm announcement publishers, audiences, publication controls, and whether automatic notifications are required. In-system result publication is explicit, but automatic notification of that publication is not.

## 12. Edge cases

These are **Inferred analysis scenarios**, not additional accepted features. Stated constraints give some expected outcomes; other outcomes remain open.

| Scenario | Grounded outcome or unresolved behavior | Reference |
|---|---|---|
| Registration outside its configured window | Not allowed by the time restriction; exact boundary behavior is open. | REQ-03; Q-04 |
| A fourth member or a second simultaneous group membership is attempted | Would violate explicit limits; scope of the one-group rule needs confirmation. | REQ-12–13; Q-08 |
| Group has no leader or two leaders | Violates the stated one-leader composition; temporary draft-group rules are unstated. | REQ-12; Q-07 |
| Group attempts a second topic or an unpublished topic | Would violate the selection rules; rejected/withdrawn registrations are unspecified. | REQ-14–15; Q-09 |
| Two groups select the same topic | No per-topic group capacity or conflict-resolution rule is stated. | Q-10 |
| Topic has zero or three supervisors | Outside the stated one-to-two range; draft-stage enforcement is open. | REQ-10; Q-06 |
| Supervisor tries to grade their topic, including a co-supervised topic | Conflicts with the explicit grading prohibition. | REQ-25 |
| Council has two or six members, or a required position is missing | Violates council composition; vacancies/replacements are unspecified. | REQ-20–21; Q-12 |
| Leader is absent or replaced near report submission | Only the leader may submit; replacement/delegation procedure is unstated. | REQ-17; Q-11 |
| Component score is missing, invalid, late, or corrected | The arithmetic-mean rule does not define these cases. | REQ-23; Q-13 |
| Review is incomplete but publication is attempted | Publication is stated to follow completion; determining completion is open. | REQ-26; Q-16 |
| Period type changes after conditional dates are configured | Applicability rules exist; handling existing dates is unstated. | REQ-06–07; Q-02 |
| Multiple users concurrently change membership or registrations | **I, INF-07:** resulting data should still respect explicit cardinality limits; concurrency mechanisms and user feedback are unspecified. | REQ-12–15 |

## 13. Assumptions

No assumption below is promoted to an explicit requirement.

| ID | Working assumption | Why it is used / confirmation |
|---|---|---|
| A-01 | “Topic” and “project” describe the proposed subject and its execution in one lifecycle. | Organizes the English analysis; confirm whether separate reusable topic and execution records are needed (Q-05). |
| A-02 | Lecturer specializations such as supervisor and council chair are contextual business roles, potentially held by the same person in different contexts. | Avoids inventing separate account types; permissible combinations remain open (Q-12, Q-18). |
| A-03 | The one-page document is the complete source for this analysis. | No supplementary policy is imported. Any external faculty regulations must be supplied/confirmed (Q-20). |

No assumption is made about one group per topic, weighted grades, specific approval roles, administrator powers, notification channels, or required integrations.

## 14. Ambiguous requirements

| Ambiguity | Why the source is insufficient | Customer question |
|---|---|---|
| Approval vs. acceptance | Topic approval and group acceptance are mentioned separately without decision owners or transitions. | Q-01, Q-09 |
| “Only configured when” for special dates | Limits applicability but does not unambiguously establish mandatory entry for eligible types. | Q-02 |
| Two separate registration stages | Sequence is clear conceptually; overlap and publication/transition rules are not. | Q-03 |
| Topic registration | Lecturers register and groups select published topics; student topic proposals are not established. | Q-05 |
| “Throughout execution” for one-group membership | Could apply within a period, type, or all concurrent work. | Q-08 |
| One topic per group | Does not define how many groups may share a topic. | Q-10 |
| Review/council applicability | Review is described generally, while the council report date is KLTN-only. | Q-15 |
| Component-score arithmetic mean | Components, missing scores, scale, and rounding are undefined. | Q-13 |
| Chair's final review result vs. final topic score | Relationship between consolidation and arithmetic calculation is not detailed. | Q-14 |
| Student result visibility | Own-topic viewing is granted; wider visibility is not addressed. | Q-17 |
| Account management, authorization, and security | Broad capabilities lack operations, role matrix, and measurable controls. | Q-18, Q-20 |

## 15. Questions that must be confirmed with the customer

These are **Open questions**, not proposed requirements. Confirm the relevant answers before implementing the affected behavior.

| ID | Question |
|---|---|
| Q-01 | Who approves topics, accepts group registrations, assigns supervisors/reviewers, forms councils, and publishes topic lists? Are topic approval and group acceptance separate decisions? Can the faculty head delegate period creation or authorize edits? |
| Q-02 | Are the reviewer deadline and council report date mandatory for eligible types? Can a period's type/dates change after registration starts, and what happens to dates that become inapplicable? |
| Q-03 | Must lecturer registration/publication finish before student registration starts? How are stages opened/closed and the topic list declared published? |
| Q-04 | Which timezone and exact start/end boundary rules apply? Are late registration or deadline exceptions permitted, and by whom? |
| Q-05 | What topic fields constitute “clear content”? May students propose topics? Can topics be reused across periods, and who manages department catalogues? |
| Q-06 | At what point must one or two supervisors be assigned, and may assignments change during execution? |
| Q-07 | Is a one-student group permitted? Who creates groups, adds/removes members, selects/replaces the leader, and submits topic registration? Are draft groups allowed? |
| Q-08 | Does one-group membership apply per period, per project type, or across all concurrent projects? When does execution begin/end for this restriction? |
| Q-09 | What happens after rejection, withdrawal, or a requested topic change? What lifecycle states and re-registration rules apply? |
| Q-10 | How many groups may register for one topic, and how should competing registrations be resolved? |
| Q-11 | What report format, size, submission deadline, replacement/version rules, and viewer permissions apply? What is the procedure when a leader cannot submit? |
| Q-12 | Must chair and secretary be distinct lecturers? Who grades, can a supervisor serve in a non-grading council role, and how are council conflicts/vacancies handled? What does the secretary do? |
| Q-13 | Which component scores enter the arithmetic mean? What scale, precision, rounding, missing/late-score handling, and correction permissions apply? How does the reviewer submission deadline affect grading? |
| Q-14 | How does chair consolidation produce the final review result, and how does that relate to the calculated topic score? Is any final confirmation needed? |
| Q-15 | Which review/council procedures apply to each of course projects, NCKH, TLCN, and KLTN? How are non-KLTN reviews scheduled? |
| Q-16 | What proves review and grading are complete? Are results released per topic, council, or period, by which account role, and can published results be corrected? |
| Q-17 | May students view results for other topics? What results may lecturers, supervisors, reviewers, and faculty staff view before and after publication? |
| Q-18 | What account operations, identity method, role combinations, department scopes, and administrative permissions are required? Who manages accounts and announcements? |
| Q-19 | Are any external-system integrations required? If so, which systems and what data/operations are involved? |
| Q-20 | What security, privacy, retention, backup, performance, availability, accessibility, language, and deployment requirements or existing faculty policies must be satisfied? |
| Q-21 | Are generated reports or exports required beyond the in-system topic list and results? Specify content, format, and access rights if needed. |
| Q-22 | Who publishes announcements, to which audiences, and with what controls? Are automatic event alerts or external delivery channels required? |

## 16. Acceptance criteria when they can be inferred

All criteria below are **Inferred** testable formulations of explicit requirements. They do not resolve the open policy choices; confirmation is needed before adopting them as a complete acceptance baseline.

| ID | Inferred acceptance criterion | Basis / limitation |
|---|---|---|
| AC-01 | A faculty head can create a named period of each stated type with lecturer and student registration start/end values. | REQ-04–05; validation details open in Q-02–04. |
| AC-02 | Reviewer score-deadline configuration is applicable only to TLCN/KLTN; council report-date configuration is applicable only to KLTN. | REQ-06–07; does not assume eligible fields are mandatory. |
| AC-03 | Registration is permitted during the corresponding configured window and prevented outside it. | REQ-03; exact boundary tests await Q-04. |
| AC-04 | The process supports topic-list preparation/publication followed by group selection from that published list. | REQ-02, REQ-15; transition policy awaits Q-03. |
| AC-05 | A topic in the applicable operational state belongs to one department, has content, and has one or two supervisors. | REQ-09–10; state and content validation await Q-05–06. |
| AC-06 | Group participation cannot result in more than three members or more than one leader; an operational group has a leader. | REQ-12; draft/minimum-size policy awaits Q-07. |
| AC-07 | A student cannot belong to a second group within the confirmed execution scope. | REQ-13; scope awaits Q-08. |
| AC-08 | A group cannot hold a second registered topic or register an unpublished topic within the confirmed registration lifecycle. | REQ-14–15; replacement rules await Q-09. |
| AC-09 | The lifecycle supports registration acceptance followed by execution under assigned supervision. | REQ-16; approval actor/process awaits Q-01. |
| AC-10 | The group's leader can submit its report; another group member cannot submit it. | REQ-17; format and lifecycle constraints await Q-11. |
| AC-11 | A formed council has three to five lecturers and includes a chair and secretary. | REQ-19–21; dual-position rule awaits Q-12. |
| AC-12 | A chair can consolidate council members' evaluations and scores into a final review result. | REQ-22; detailed finalization awaits Q-14. |
| AC-13 | For a complete, valid set of agreed component scores, the final score equals their sum divided by their count. | REQ-23; component definition and rounding await Q-13. |
| AC-14 | A lecturer can grade multiple eligible topics but cannot grade a topic they supervise, including as one of two supervisors. | REQ-24–25; eligible-assignment rules await Q-12–13. |
| AC-15 | Results can be published after the confirmed review/grading completion conditions are met, and participating students can view their topic's scores/results. | REQ-26–27; completion and wider visibility await Q-16–17. |
| AC-16 | Official school and faculty announcements can be posted and made available to the intended users. | REQ-30; publisher/audience rules await Q-18, Q-22. |

Account management and security are explicit but too broad for meaningful pass/fail criteria until Q-18 and Q-20 are answered. The criteria above are not an exhaustive test suite.

## 17. Requirement traceability table

All `REQ` rows are **Explicit**. Feature/module labels are analysis groupings, not prescribed application architecture. All references are to the sole physical PDF page; paragraph/topic locators aid review.

| Requirement ID | Description | Source PDF page | Feature/module |
|---|---|---|---|
| REQ-01 | Automate faculty topic management for course projects, NCKH, TLCN, and KLTN across registration, approval, assignments, grading, and result publication. | 1, opening paragraph | Overall lifecycle |
| REQ-02 | Organize registration into topic-list preparation/publication and subsequent group selection stages. | 1, registration paragraph | Registration stages |
| REQ-03 | Permit registration according to prescribed time windows. | 1, registration paragraph | Registration timing |
| REQ-04 | Faculty head creates registration periods with a name and one of the four stated types. | 1, registration paragraph | Period management |
| REQ-05 | Period configuration includes lecturer registration start/end and student registration start/end. | 1, registration paragraph | Period management |
| REQ-06 | Configure the deadline for reviewer scores submitted to the faculty only for TLCN or KLTN. | 1, registration paragraph | Review deadlines |
| REQ-07 | Configure the council report date only for KLTN. | 1, registration paragraph | Council scheduling |
| REQ-08 | Support lecturer topic registration and topic approval. | 1, opening and registration paragraphs | Topic registration/approval |
| REQ-09 | Manage topic lists by department; each topic belongs to a specific department and has clear content. | 1, topic paragraph | Topic catalogue |
| REQ-10 | Each topic is supervised by at least one and at most two lecturers. | 1, topic paragraph | Supervision |
| REQ-11 | Support assignment of supervisors and reviewers. | 1, opening paragraph | Lecturer assignments |
| REQ-12 | Students participate in groups of at most three members with one group leader. | 1, group paragraph | Group management |
| REQ-13 | Each student participates in only one group throughout project execution. | 1, group paragraph | Group membership |
| REQ-14 | Each group registers for only one topic. | 1, group paragraph | Group registration |
| REQ-15 | Group topic selection is from the published topic list. | 1, registration and group paragraphs | Group registration |
| REQ-16 | After registration and acceptance, groups execute the project under assigned lecturer guidance. | 1, group paragraph | Approval/execution |
| REQ-17 | Only the group leader may submit the project report. | 1, group paragraph | Report submission |
| REQ-18 | After completion, student groups must participate in review according to plan. | 1, review paragraph | Review process |
| REQ-19 | Manage formation of review councils. | 1, review paragraph | Council management |
| REQ-20 | Each review council consists of three to five lecturers. | 1, review paragraph | Council composition |
| REQ-21 | Each council includes one chair and one secretary. | 1, review paragraph | Council roles |
| REQ-22 | Chair consolidates members' evaluations and scores to determine the final topic review result. | 1, review paragraph | Review consolidation |
| REQ-23 | Calculate final topic score as the arithmetic mean of component scores. | 1, review paragraph | Grade calculation |
| REQ-24 | A lecturer may grade multiple topics. | 1, review paragraph | Grading |
| REQ-25 | A lecturer must not grade a topic they supervise. | 1, review paragraph | Grading authorization |
| REQ-26 | Publish evaluation results on the system after review and grading are complete; faculty publication is identified in the opening lifecycle. | 1, opening and results paragraphs | Result publication |
| REQ-27 | Students can view scores and evaluation results for the topic they participate in. | 1, results paragraph | Student results |
| REQ-28 | Support user-account management. | 1, final paragraph | Account management |
| REQ-29 | Provide information to system users through official announcements. | 1, final paragraph | User information |
| REQ-30 | Support posting official school and faculty announcements to provide timely information. | 1, final paragraph | Announcements |
| REQ-31 | Provide authorization solutions. | 1, final paragraph | Authorization |
| REQ-32 | Provide data-security solutions. | 1, final paragraph | Security |

REQ-29 and REQ-30 express the audience/purpose and publishing capability of the same source statement; they do not imply separate notification channels or separate features.

### Inference traceability (not additional explicit requirements)

| Requirement ID | Description | Source PDF page | Feature/module |
|---|---|---|---|
| INF-01 | Shared process coordinates the stated lifecycle. | 1, derived from REQ-01–03, REQ-26 | Business objectives |
| INF-02 | Consistent application of stated restrictions supports the business objective. | 1, derived from REQ-10, REQ-12–15, REQ-20, REQ-25 | Business objectives |
| INF-03 | Account management and publishing need responsible operators; no administrator role is established. | 1, derived from REQ-28, REQ-30 | Operational roles |
| INF-04 | Membership, leadership, supervision, and grading relationships need to be represented to evaluate restrictions. | 1, derived from REQ-10–17, REQ-25 | Data relationships |
| INF-05 | Own-topic-only student result access is a possible privacy interpretation, pending Q-17. | 1, derived from REQ-27, REQ-31–32 | Result permissions |
| INF-06 | Acting-user identity and relevant roles/relationships must be distinguishable to apply stated permissions. | 1, derived from REQ-04, REQ-17, REQ-25, REQ-31 | Identity/authorization |
| INF-07 | Concurrent changes should preserve the stated group/topic limits. | 1, derived from REQ-12–15 | Data consistency |

Acceptance-criterion derivations are mapped individually in section 16. Assumptions and questions have no independent source authority and do not add to the explicit requirement baseline.
