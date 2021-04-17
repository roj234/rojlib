package roj.math;

import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/15 9:04
 */
public interface MPShape {
    /**
     * 矩形边界
     */
    Rect2d getBounds();

    /**
     * 点, 有序, 顺时针
     */
    List<Vec2d> getPoints();
}
