package de.releaseteam.jiro.protocol;

import java.util.Objects;

public class FieldSchema {
    private final int id;
    private final String name;
    private final int type;
    private final int role;

    public FieldSchema(int id, String name, int type, int role) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getType() {
        return type;
    }

    public int getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FieldSchema)) return false;
        FieldSchema that = (FieldSchema) o;
        return id == that.id && type == that.type && role == that.role && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, role);
    }
}
