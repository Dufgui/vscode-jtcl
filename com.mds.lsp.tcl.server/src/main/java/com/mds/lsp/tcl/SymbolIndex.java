package com.mds.lsp.tcl;

import tcl.lang.Parser;
import tcl.lang.RelocatedParser;
import tcl.lang.TclParse;
import tcl.lang.TclToken;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class SymbolIndex {



    private final Path workspaceRoot;
    private final Supplier<Collection<URI>> openFiles;
    private final Function<URI, Optional<String>> activeContent;
    private final RelocatedParser parser = new RelocatedParser();

    /** Source path files, for which we support methods and classes */
    private final Map<URI, SourceFileIndex> sourcePathFiles = new ConcurrentHashMap<>();

    private final CompletableFuture<?> finishedInitialIndex = new CompletableFuture<>();

    SymbolIndex(
            Path workspaceRoot,
            Supplier<Collection<URI>> openFiles,
            Function<URI, Optional<String>> activeContent) {
        this.workspaceRoot = workspaceRoot;
        this.openFiles = openFiles;
        this.activeContent = activeContent;

        new Thread(this::initialIndex, "Initial-Index").start();
    }


    private void initialIndex() {
        // TODO send a progress bar to the user
        updateIndex(InferConfig.allTclFiles(workspaceRoot).map(Path::toUri));

        finishedInitialIndex.complete(null);
    }

    private void updateIndex(Stream<URI> files) {
        files.forEach(this::updateFile);
    }

    private void updateFile(URI each) {
        if (needsUpdate(each)) {
            List<TclParse> parse = parse(each);
            update(each, parse);
        }
    }


    private boolean needsUpdate(URI file) {
        if (!sourcePathFiles.containsKey(file)) return true;
        else {
            try {
                Instant updated = sourcePathFiles.get(file).updated;
                Instant modified = Files.getLastModifiedTime(Paths.get(file)).toInstant();

                return updated.isBefore(modified);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** Update a file in the index */
    private void update(URI file, List<TclParse> tclParses) {
        SourceFileIndex index = new SourceFileIndex();
        for (TclParse parse: tclParses) {
            for (int i = 0; i < parse.numTokens(); i++) {
                TclToken token = parse.getToken(i);
                switch (token.getType()) {
                case Parser.TCL_TOKEN_WORD:
                case Parser.TCL_TOKEN_SIMPLE_WORD:
                case Parser.TCL_TOKEN_TEXT:
                case Parser.TCL_TOKEN_BS:
                case Parser.TCL_TOKEN_COMMAND:
                case Parser.TCL_TOKEN_VARIABLE:
                case Parser.TCL_TOKEN_SUB_EXPR:
                case Parser.TCL_TOKEN_OPERATOR:
                //TODO feed the index
                }
            }

        }
        sourcePathFiles.put(file, index);
    }


    private List<TclParse> parse(URI source) {
        return RelocatedParser.parseCommand(source.getPath());
    }

    Set<Path> sourcePath() {
        updateOpenFiles();

        Set<Path> result = new HashSet<>();

        sourcePathFiles.forEach(
                (uri, index) -> {
                    Path dir = Paths.get(uri).getParent();
                    result.add(dir);

                });

        return result;
    }

    private void updateOpenFiles() {
        finishedInitialIndex.join();

        updateIndex(openFiles.get().stream());
    }

    private static final Logger LOG = Logger.getLogger("main");
}
