package vn.pmgteam.jmmd.base;

import java.io.IOException;
import java.nio.file.Path;

public interface MmdReader {
    MmdModel read(Path file) throws IOException;
}