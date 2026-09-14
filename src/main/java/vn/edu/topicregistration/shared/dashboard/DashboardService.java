package vn.edu.topicregistration.shared.dashboard;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.topicregistration.identity.domain.RoleCode;
import vn.edu.topicregistration.shared.dashboard.DashboardView.Focus;
import vn.edu.topicregistration.shared.dashboard.DashboardView.Metric;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy;
import vn.edu.topicregistration.shared.security.AuthorizationPolicy.ActorIdentity;
import vn.edu.topicregistration.shared.time.ProjectTime;

@Service
@Profile("!preview & !test")
public class DashboardService {
    private final AuthorizationPolicy authorization;
    private final DashboardQuery query;
    private final ProjectTime time;

    public DashboardService(
            AuthorizationPolicy authorization, DashboardQuery query, ProjectTime time) {
        this.authorization = authorization;
        this.query = query;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public DashboardView current() {
        ActorIdentity actor = authorization.currentActor();
        // A dean can also hold LECTURER, but may not use the authoring workflow.
        if (actor.roles().contains(RoleCode.DEAN)) {
            return dean(actor);
        }
        if (actor.roles().contains(RoleCode.LECTURER)) {
            return lecturer(actor);
        }
        if (actor.roles().contains(RoleCode.STUDENT)) {
            return student(actor);
        }
        if (actor.roles().contains(RoleCode.ADMIN)) {
            return new DashboardView("Quản trị sự cố", "Tổng quan quản trị",
                    "Tài khoản quản trị chỉ xử lý sự cố; không có quyền xem hoặc quyết định học vụ.",
                    List.of(), new Focus("PHẠM VI QUYỀN", "Không có dữ liệu học vụ",
                    "Đề tài, nhóm và đăng ký thuộc phạm vi giảng viên, trưởng khoa và sinh viên.",
                    null, null, "bg-secondary-lt"));
        }
        throw new AccessDeniedException("Unsupported dashboard role");
    }

    private DashboardView dean(ActorIdentity actor) {
        long facultyId = required(actor.facultyId());
        long pendingTopics = query.countFacultyTopics(facultyId, "PENDING_APPROVAL");
        long approvedTopics = query.countFacultyTopics(facultyId, "APPROVED");
        long pendingRegistrations = query.countFacultyPendingRegistrations(facultyId);
        long periods = query.countFacultyPeriods(facultyId);
        Focus focus = pendingRegistrations > 0
                ? new Focus("VIỆC CẦN XỬ LÝ", "Có " + pendingRegistrations + " đăng ký chờ xét",
                        "Mở đợt đăng ký để xem nhóm và quyết định chấp nhận hoặc từ chối.",
                        "/periods", "Xem các đợt", "bg-yellow-lt")
                : pendingTopics > 0
                ? new Focus("VIỆC CẦN XỬ LÝ", "Có " + pendingTopics + " đề tài chờ duyệt",
                        "Mở đợt đăng ký để duyệt từng đề xuất của giảng viên.",
                        "/periods", "Duyệt đề tài", "bg-yellow-lt")
                : approvedTopics > 0
                ? new Focus("VIỆC CẦN XỬ LÝ", "Có " + approvedTopics + " đề tài đã duyệt",
                        "Đề tài đã duyệt cần được công bố trong đợt để sinh viên nhìn thấy.",
                        "/periods", "Xem các đợt", "bg-blue-lt")
                : new Focus("TÌNH TRẠNG", "Không có hồ sơ chờ xử lý",
                        "Các đề xuất và đăng ký mới của khoa sẽ xuất hiện tại đây.",
                        "/periods", "Xem các đợt", "bg-green-lt");
        return new DashboardView("Trưởng khoa", "Tổng quan duyệt học vụ",
                "Chỉ hiển thị đề tài, đăng ký và đợt thuộc khoa của bạn.", List.of(
                new Metric("Đề tài chờ duyệt", pendingTopics, "Đề xuất giảng viên chưa được quyết định", "/periods"),
                new Metric("Đề tài chờ công bố", approvedTopics, "Đã duyệt nhưng sinh viên chưa thấy", "/periods"),
                new Metric("Đăng ký chờ xét", pendingRegistrations, "Nhóm sinh viên chờ quyết định", "/periods"),
                new Metric("Đợt đăng ký", periods, "Các đợt của khoa", "/periods")), focus);
    }

    private DashboardView lecturer(ActorIdentity actor) {
        long accountId = required(actor.accountId());
        long departmentId = required(actor.departmentId());
        long facultyId = required(actor.facultyId());
        long drafts = query.countLecturerTopics(accountId, departmentId, facultyId, "DRAFT");
        long pending = query.countLecturerTopics(accountId, departmentId, facultyId, "PENDING_APPROVAL");
        long approved = query.countLecturerTopics(accountId, departmentId, facultyId, "APPROVED");
        long published = query.countLecturerTopics(accountId, departmentId, facultyId, "PUBLISHED");
        Focus focus = drafts > 0
                ? new Focus("VIỆC CỦA BẠN", "Có " + drafts + " đề tài nháp",
                        "Hoàn thiện nội dung và gửi trưởng khoa duyệt khi đợt còn mở cho giảng viên.",
                        "/topics", "Xem đề tài của tôi", "bg-blue-lt")
                : pending > 0
                ? new Focus("TRẠNG THÁI ĐỀ TÀI", "Đề tài đang chờ duyệt",
                        "Bạn có thể theo dõi quyết định của trưởng khoa trong danh sách đề tài.",
                        "/topics", "Xem đề tài của tôi", "bg-yellow-lt")
                : new Focus("ĐỀ TÀI CỦA TÔI", "Theo dõi đề tài đã đề xuất",
                        "Tạo đề tài trong đợt phù hợp hoặc xem trạng thái công bố.",
                        "/topics", "Xem đề tài của tôi", "bg-green-lt");
        return new DashboardView("Giảng viên", "Tổng quan đề tài của tôi",
                "Theo dõi đề tài bạn đề xuất trong bộ môn của mình.", List.of(
                new Metric("Bản nháp", drafts, "Chưa gửi duyệt", "/topics"),
                new Metric("Chờ duyệt", pending, "Đã gửi trưởng khoa", "/topics"),
                new Metric("Đã duyệt", approved, "Chưa công bố cho sinh viên", "/topics"),
                new Metric("Đã công bố", published, "Sinh viên có thể xem trong khoa", "/topics")), focus);
    }

    private DashboardView student(ActorIdentity actor) {
        long studentId = required(actor.accountId());
        long facultyId = required(actor.facultyId());
        long invitations = query.countStudentInvitations(studentId, facultyId, time.now());
        long groups = query.countStudentGroups(studentId, facultyId);
        long pending = query.countStudentRegistrations(studentId, facultyId, "PENDING");
        long accepted = query.countStudentRegistrations(studentId, facultyId, "ACCEPTED");
        Focus focus = query.latestStudentRegistration(studentId, facultyId)
                .map(registration -> "ACCEPTED".equals(registration.status())
                        ? new Focus("ĐĂNG KÝ THÀNH CÔNG", registration.title(),
                                "Trưởng khoa đã chấp nhận đăng ký đề tài của nhóm bạn.",
                                "/registrations/" + registration.id(), "Xem đăng ký", "bg-green-lt")
                        : new Focus("ĐĂNG KÝ ĐANG CHỜ", registration.title(),
                                "Nhóm đã gửi đăng ký; hãy theo dõi quyết định của trưởng khoa.",
                                "/registrations/" + registration.id(), "Xem đăng ký", "bg-yellow-lt"))
                .orElseGet(() -> invitations > 0
                        ? new Focus("VIỆC CẦN LÀM", "Bạn có lời mời vào nhóm",
                                "Xem và phản hồi lời mời trước khi hết hạn đăng ký sinh viên.",
                                "/group-invitations", "Xem lời mời", "bg-blue-lt")
                        : new Focus("BẮT ĐẦU", "Chưa có đăng ký đề tài hiệu lực",
                                "Xem nhóm của bạn hoặc tham gia nhóm để đăng ký đề tài đã công bố.",
                                "/groups", "Xem nhóm", "bg-secondary-lt"));
        return new DashboardView("Sinh viên", "Tổng quan đăng ký của tôi",
                "Theo dõi lời mời, nhóm và kết quả đăng ký của chính bạn.", List.of(
                new Metric("Lời mời cần phản hồi", invitations, "Lời mời còn trong hạn đăng ký", "/group-invitations"),
                new Metric("Nhóm đang tham gia", groups, "Nhóm hiện tại trong các đợt", "/groups"),
                new Metric("Đăng ký chờ xét", pending, "Đã gửi và đang chờ trưởng khoa", "/groups"),
                new Metric("Đăng ký được chấp nhận", accepted, "Đăng ký đề tài thành công", "/groups")), focus);
    }

    private static long required(Long value) {
        if (value == null) {
            throw new AccessDeniedException("Missing dashboard scope");
        }
        return value;
    }
}
