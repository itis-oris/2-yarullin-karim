package common;

public class Player {
    private String id;
    private String name;
    private double x;
    private double y;
    private boolean alive;

    // Конструкторы
    public Player() {
    }

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.x = GameSettings.WORLD_WIDTH / 2;
        this.y = GameSettings.WORLD_HEIGHT / 2;
        this.alive = true;
    }

    // Геттеры и сеттеры
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    // Клонирование для потокобезопасности
    public Player clone() {
        Player clone = new Player();
        clone.setId(this.id);
        clone.setName(this.name);
        clone.setX(this.x);
        clone.setY(this.y);
        clone.setAlive(this.alive);
        return clone;
    }
}