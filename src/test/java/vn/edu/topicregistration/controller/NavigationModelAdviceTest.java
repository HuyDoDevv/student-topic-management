package vn.edu.topicregistration.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;

import vn.edu.topicregistration.entity.RoleCode;
import vn.edu.topicregistration.security.AccountPrincipal;

/** T008: verifies review navigation is exposed only to the intended persisted roles. */
class NavigationModelAdviceTest {
    private final NavigationModelAdvice advice = new NavigationModelAdvice();

    @Test
    void deanLecturerGetsBoardsScoringAndFacultyResultsButNotStudentResults() {
        ExtendedModelMap model = modelFor(RoleCode.DEAN, RoleCode.LECTURER);

        assertEquals(true, model.get("canManageBoards"));
        assertEquals(true, model.get("canScoreBoards"));
        assertEquals(true, model.get("canViewFacultyResults"));
        assertEquals(false, model.get("canViewStudentResults"));
        assertEquals(false, model.get("canAuthorTopics"));
        assertEquals(true, model.get("canManageAnnouncements"));
    }

    @Test
    void studentGetsOnlyStudentReviewResultNavigation() {
        ExtendedModelMap model = modelFor(RoleCode.STUDENT);

        assertEquals(false, model.get("canManageBoards"));
        assertEquals(false, model.get("canScoreBoards"));
        assertEquals(false, model.get("canViewFacultyResults"));
        assertEquals(true, model.get("canViewStudentResults"));
    }

    @Test
    void adminAndAnonymousUsersGetNoReviewNavigation() {
        ExtendedModelMap admin = modelFor(RoleCode.ADMIN);
        ExtendedModelMap anonymous = new ExtendedModelMap();
        advice.addCurrentAccount(null, anonymous);

        for (String attribute : Set.of(
                "canManageBoards",
                "canScoreBoards",
                "canViewFacultyResults",
                "canViewStudentResults")) {
            assertEquals(false, admin.get(attribute));
            assertEquals(false, anonymous.get(attribute));
        }
        assertEquals(true, admin.get("canManageAccounts"));
        assertEquals(true, admin.get("canManageAnnouncements"));
        assertEquals(false, anonymous.get("canManageAccounts"));
        assertEquals(false, anonymous.get("canManageAnnouncements"));
    }

    private ExtendedModelMap modelFor(RoleCode... roles) {
        Set<RoleCode> roleSet = Set.of(roles);
        AccountPrincipal principal = new AccountPrincipal(
                1L,
                "navigation-test",
                "not-a-real-hash",
                "Người dùng kiểm thử",
                true,
                roleSet,
                2L,
                3L);
        ExtendedModelMap model = new ExtendedModelMap();
        advice.addCurrentAccount(
                new UsernamePasswordAuthenticationToken(
                        principal, principal.getPassword(), principal.getAuthorities()),
                model);
        return model;
    }
}
