package com.example.honeycomb.example.cells;

import com.example.honeycomb.annotations.Cell;

@Cell(port = 9092)
public class CatalogCell {
    private String id;
    private String title;
    private String category;
    private double listPrice;

    public CatalogCell() {
    }

    public CatalogCell(String id, String title, String category, double listPrice) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.listPrice = listPrice;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getListPrice() {
        return listPrice;
    }

    public void setListPrice(double listPrice) {
        this.listPrice = listPrice;
    }
}
