package tw.niels.beverage_api_project.modules.member.entity; // 建議新路徑

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType; // 使用 Instant
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.user.entity.User;

@Entity
@Table(name = "member_points_log")
public class MemberPointLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_user_id", nullable = false)
    private User member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(name = "points_change", nullable = false)
    private Long pointsChange;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "reason", length = 255)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 無參數建構子
    public MemberPointLog() {
    }

    // 全參數建構子 (方便 Service 使用)
    public MemberPointLog(User member, Order order, Long pointsChange, Long balanceAfter, String reason) {
        this.member = member;
        this.order = order;
        this.pointsChange = pointsChange;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
    }

    // --- Getters ---
    public Long getLogId() {
        return logId;
    }

    public User getMember() {
        return member;
    }

    public Order getOrder() {
        return order;
    }

    public Long getPointsChange() {
        return pointsChange;
    }

    public Long getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

}