package com.trading.orderflow.model;

public record ImbalanceAlert(Side side, double price, double qty, double otherQty, double ratio) {
    public Side dominantSide() { return side; }
    public double dominantPrice() { return price; }

    @Override
    public String toString() {
        return String.format("%s imbalance at %.4f (%.2fx ratio)", side, price, ratio);
    }
}
