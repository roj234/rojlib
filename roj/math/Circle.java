package roj.math;

import roj.util.Hasher;

public class Circle {
    public double centerX, centerY;
    public double radius;

    public Circle(double centerX, double centerY, double radius) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
    }

    @Override
    public String toString() {
        return "Circle {(" + centerX + ',' + centerY + "), r=" + radius + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Circle circle = (Circle) o;
        return circle.centerX == centerX && circle.centerY == centerY && circle.radius == radius;
    }

    @Override
    public int hashCode() {
        return new Hasher().add(centerX).add(centerY).add(radius).getHash();
    }
}