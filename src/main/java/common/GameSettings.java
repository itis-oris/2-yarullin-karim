package common;

public class GameSettings {
    // Настройки игры
    public static final int WORLD_WIDTH = 800;
    public static final int WORLD_HEIGHT = 600;

    public static final int CELL_SIZE = 20;

    public static final int GRID_W = WORLD_WIDTH / CELL_SIZE;
    public static final int GRID_H = WORLD_HEIGHT / CELL_SIZE;


    // Цвета для раундов
    public static final String[] ROUND_COLORS = {
            "#FF0000", // Красный
            "#00FF00", // Зеленый
            "#0000FF", // Синий
            "#FFFF00", // Желтый
            "#FF00FF", // Пурпурный
            "#00FFFF"  // Голубой
    };

    // Настройки времени
    public static final double INITIAL_ROUND_TIME = 10.0; // секунд
    public static final double MIN_ROUND_TIME = 1.0; // секунд
    public static final double ROUND_TIME_DECREMENT = 0.0; // секунд за раунд
    public static final double BASE_MATCH_START_DELAY = 10.0; // секунд для 2 игроков
    public static final double PLAYER_DELAY_REDUCTION = 2.0; // секунд за игрока
    public static final double MIN_MATCH_START_DELAY = 2.0; // минимальная задержка

    // Настройки сетки
    public static final int GRID_SIZE = 40;
    public static final int NUM_SPOTS = 20;

    // Скорость движения
    public static final double MOVE_SPEED = 3.0;
}