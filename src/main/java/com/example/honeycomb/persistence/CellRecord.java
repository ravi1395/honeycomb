package com.example.honeycomb.persistence;

import com.example.honeycomb.util.HoneycombConstants;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = HoneycombConstants.Persistence.TABLE_CELL_RECORDS)
public class CellRecord {
    @Id
    @Column(name = HoneycombConstants.Persistence.COL_RECORD_KEY, length = 512)
    private String recordKey;

    @Column(name = HoneycombConstants.Persistence.COL_CELL_NAME, length = 255, nullable = false)
    private String cellName;

    @Column(name = HoneycombConstants.Persistence.COL_ITEM_ID, length = 255, nullable = false)
    private String itemId;

    @Lob
    @Column(name = HoneycombConstants.Persistence.COL_PAYLOAD_JSON, nullable = false)
    private String payloadJson;

    public CellRecord() {}

    public CellRecord(String recordKey, String cellName, String itemId, String payloadJson) {
        this.recordKey = recordKey;
        this.cellName = cellName;
        this.itemId = itemId;
        this.payloadJson = payloadJson;
    }

    public String getRecordKey() {
        return recordKey;
    }

    public void setRecordKey(String recordKey) {
        this.recordKey = recordKey;
    }

    public String getCellName() {
        return cellName;
    }

    public void setCellName(String cellName) {
        this.cellName = cellName;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }
}
