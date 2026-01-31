package com.example.honeycomb.example.cells;

import com.example.honeycomb.annotations.Cell;

@Cell(port = 9091)
public class InventoryCell {
    private String id;
    private String sku;
    private int quantity;
    private String warehouse;

    public InventoryCell() {
    }

    public InventoryCell(String id, String sku, int quantity, String warehouse) {
        this.id = id;
        this.sku = sku;
        this.quantity = quantity;
        this.warehouse = warehouse;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getWarehouse() {
        return warehouse;
    }

    public void setWarehouse(String warehouse) {
        this.warehouse = warehouse;
    }
}
