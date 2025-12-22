package tw.niels.beverage_api_project.common.entity;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import lombok.Setter;
import org.springframework.data.domain.Persistable;
import tw.niels.beverage_api_project.common.util.TsidUtil;

import java.io.Serializable;
import java.util.Objects;

/**
 * 支援 TSID 的 JPA 基底類別。
 * <p>
 * 1. 自動在建構時生成 TSID。
 * 2. 實作 Persistable 介面，解決手動指派 ID 導致 save() 誤判為 update 的問題。
 * </p>
 */
@MappedSuperclass
public abstract class BaseTsidEntity implements Persistable<Long>, Serializable {

    // 提供 setter 以便在某些特殊情況下 (如資料匯入) 手動設定 ID
    // 移除 @GeneratedValue，改為手動指派
    @Setter
    @Id
    private Long id;

    // 透過 Transient 欄位判斷是否為新物件
    @Transient
    private boolean isNew = true;

    public BaseTsidEntity() {
        // 在物件建立時自動生成 TSID
        this.id = TsidUtil.nextId();
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    // JPA 載入後 (從 DB 讀取) 或 儲存後，將 isNew 設為 false
    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseTsidEntity that = (BaseTsidEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}