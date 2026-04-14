package com.hayden.utilitymodule.io;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public interface ArchiveUtils {

    static Result<List<String>, SingleError> tarRepos(Path basePath, String outFile, String inFiles) {
        try {
            return Result.ok(
                    createTarArchive(basePath.resolve(outFile).toString(), basePath.resolve(inFiles)
                            .toString()));
        } catch (IOException e) {
            e.printStackTrace();
            return Result.err(SingleError.fromE(e));
        }
    }

    static Result<List<String>, SingleError> prepareTestRepos(Path basePath, String tarFile) {
        return prepareTestRepos(basePath, basePath, tarFile) ;
    }

    static Result<List<String>, SingleError> prepareTestRepos(Path basePath, Path outPath, String tarFile) {

        var out = outPath.toFile();
        out.mkdirs();

        try(
                FileInputStream fileInputStream = new FileInputStream(basePath.resolve(tarFile).toFile());
                TarArchiveInputStream inputStream = new TarArchiveInputStream(fileInputStream)
        ) {
            var l = new ArrayList<String>();
            var curr = inputStream.getCurrentEntry();
            if (curr == null)
                curr = inputStream.getNextEntry();

            if (curr == null)
                return Result.err(SingleError.fromMessage("No data in " + tarFile));

            do {
                Path toSave = out.toPath().resolve(curr.getName());
                Path fileName = toSave.getFileName();
                if (fileName.toString().startsWith("._"))
                    continue;
                if (curr.isDirectory()) {
                    toSave.toFile().mkdirs();
                } else {
                    final OutputStream outputFileStream = new FileOutputStream(toSave.toFile());
                    IOUtils.copy(inputStream, outputFileStream);
                    outputFileStream.close();
                }

                l.add(curr.getName());
            } while ((curr = inputStream.getNextEntry()) != null);

            return Result.ok(l);
        } catch (IOException e) {
            return Result.err(SingleError.fromE(e));
        }

    }

    static List<String> createTarArchive(String sourceDirectory, String archiveFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(archiveFile);
             TarArchiveOutputStream tarOut = new TarArchiveOutputStream(fos)) {

            File sourceDir = new File(sourceDirectory);
            if (!sourceDir.exists() || !sourceDir.isDirectory()) {
                throw new IOException("Source directory does not exist or is not a directory: " + sourceDirectory);
            }

            return addFilesToTar(sourceDir, tarOut, ""); // Start recursion from the source directory
        }
    }

    private static List<String> addFilesToTar(File file, TarArchiveOutputStream tarOut, String entryPath) throws IOException {
        List<String> out = new ArrayList<>();
        String currentEntryPath = entryPath.isEmpty() ? file.getName() : entryPath + "/" + file.getName();
        var tarEntry = tarOut.createArchiveEntry(file, currentEntryPath);
        tarOut.putArchiveEntry(tarEntry);

        if (file.isFile()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                IOUtils.copy(fis, tarOut);
            }
            tarOut.closeArchiveEntry();
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    out.add(child.toString());
                    out.addAll(addFilesToTar(child, tarOut, currentEntryPath));
                }
            }
        }

        return out;
    }

}
