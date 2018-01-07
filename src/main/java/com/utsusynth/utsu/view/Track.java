package com.utsusynth.utsu.view;

import java.util.List;
import com.google.common.base.Optional;
import com.google.inject.Inject;
import com.utsusynth.utsu.UtsuController.Mode;
import com.utsusynth.utsu.common.PitchUtils;
import com.utsusynth.utsu.common.data.AddResponse;
import com.utsusynth.utsu.common.data.EnvelopeData;
import com.utsusynth.utsu.common.data.NeighborData;
import com.utsusynth.utsu.common.data.NoteData;
import com.utsusynth.utsu.common.data.PitchbendData;
import com.utsusynth.utsu.common.data.RemoveResponse;
import com.utsusynth.utsu.common.exception.NoteAlreadyExistsException;
import com.utsusynth.utsu.common.quantize.Quantizer;
import com.utsusynth.utsu.common.quantize.Scaler;
import com.utsusynth.utsu.view.note.TrackNote;
import com.utsusynth.utsu.view.note.TrackNoteCallback;
import com.utsusynth.utsu.view.note.TrackNoteFactory;
import com.utsusynth.utsu.view.note.envelope.TrackEnvelopeCallback;
import com.utsusynth.utsu.view.note.portamento.TrackPortamentoCallback;
import javafx.scene.Group;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

public class Track {
    private final Highlighter highlighter;
    private final TrackNoteFactory noteFactory;
    private final TrackNoteMap noteMap;
    private final Scaler scaler;

    private GridPane track;
    private GridPane dynamics;
    private int numMeasures;
    private ViewCallback model;

    @Inject
    public Track(
            Highlighter highlighter,
            TrackNoteFactory trackNoteFactory,
            TrackNoteMap noteMap,
            Scaler scaler) {
        this.highlighter = highlighter;
        this.noteFactory = trackNoteFactory;
        this.noteMap = noteMap;
        this.scaler = scaler;
    }

    public void initialize(ViewCallback callback) {
        this.model = callback;
    }

    public GridPane createNewTrack(List<NoteData> notes) {
        clearTrack();
        if (notes.isEmpty()) {
            return track;
        }

        // Add as many octaves as needed.
        NoteData lastNote = notes.get(notes.size() - 1);
        setNumMeasures((lastNote.getPosition() / Quantizer.COL_WIDTH / 4) + 4);

        // Add all notes.
        NoteData prevNote = notes.get(0);
        for (NoteData note : notes) {
            TrackNote newNote = noteFactory.createNote(note, noteCallback);
            int position = note.getPosition();
            try {
                noteMap.putNote(position, newNote);
                if (note.getEnvelope().isPresent()) {
                    noteMap.putEnvelope(
                            position,
                            note.getEnvelope().get(),
                            getEnvelopeCallback(position));
                }
                if (note.getPitchbend().isPresent()) {
                    noteMap.putPortamento(
                            position,
                            prevNote.getPitch(),
                            note.getPitchbend().get(),
                            getPitchbendCallback(position));
                }
            } catch (NoteAlreadyExistsException e) {
                // TODO: Throw an error here?
                System.out.println("UST read found two notes in the same place :(");
            }
            noteMap.addNoteElement(newNote);
            prevNote = note;
        }
        return track;
    }

    public Group getNotesElement() {
        return noteMap.getNotesElement();
    }

    public GridPane getDynamicsElement() {
        if (dynamics == null) {
            // TODO: Handle this;
            System.out.println("Dynamics element is empty!");
        }
        return dynamics;
    }

    public Group getEnvelopesElement() {
        return noteMap.getEnvelopesElement();
    }

    public Group getPitchbendsElement() {
        return noteMap.getPitchbendsElement();
    }

    private void clearTrack() {
        // Remove current track.
        highlighter.clearHighlights();
        noteMap.clear();
        track = new GridPane();
        dynamics = new GridPane();

        numMeasures = 0;
        setNumMeasures(4);
    }

    private void setNumMeasures(int newNumMeasures) {
        if (newNumMeasures < 0) {
            return;
        } else if (newNumMeasures > numMeasures) {
            for (int i = numMeasures; i < newNumMeasures; i++) {
                addMeasure();
            }
        } else if (newNumMeasures == numMeasures) {
            // Nothing needs to be done.
            return;
        } else {
            // Remove measures.
            int desiredNumColumns = newNumMeasures * 4;
            track.getChildren().removeIf((child) -> {
                return GridPane.getColumnIndex(child) >= desiredNumColumns;
            });
            // Remove dynamics columns.
            dynamics.getChildren().removeIf((child) -> {
                return GridPane.getColumnIndex(child) >= desiredNumColumns;
            });
            numMeasures = newNumMeasures;
        }
    }

    private void addMeasure() {
        int rowNum = 0;
        int numColumns = numMeasures * 4;
        for (int octave = 7; octave > 0; octave--) {
            for (String pitch : PitchUtils.REVERSE_PITCHES) {
                // Add row to track.
                for (int colNum = numColumns; colNum < numColumns + 4; colNum++) {
                    AnchorPane newCell = new AnchorPane();
                    newCell.setPrefSize(
                            Math.round(scaler.scaleX(Quantizer.COL_WIDTH)),
                            Math.round(scaler.scaleY(Quantizer.ROW_HEIGHT)));
                    newCell.getStyleClass().add("track-cell");
                    newCell.getStyleClass().add(pitch.endsWith("#") ? "black-key" : "white-key");
                    if (colNum % 4 == 0) {
                        newCell.getStyleClass().add("measure-start");
                    } else if ((colNum + 1) % 4 == 0) {
                        newCell.getStyleClass().add("measure-end");
                    }

                    final int currentRowNum = rowNum;
                    final int currentColNum = colNum;
                    newCell.setOnMouseClicked((event) -> {
                        Mode currentMode = model.getCurrentMode();
                        if (currentMode == Mode.ADD) {
                            // Create note.
                            TrackNote newNote = noteFactory
                                    .createDefaultNote(currentRowNum, currentColNum, noteCallback);
                            noteMap.addNoteElement(newNote);
                        }
                        // Clear highlights regardless of current mode.
                        highlighter.clearHighlights();
                    });
                    track.add(newCell, colNum, rowNum);
                }
                rowNum++;
            }
        }

        // Add new columns to dynamics.
        for (int colNum = numColumns; colNum < numColumns + 4; colNum++) {
            AnchorPane topCell = new AnchorPane();
            topCell.setPrefSize(scaler.scaleX(Quantizer.COL_WIDTH), 50);
            topCell.getStyleClass().add("dynamics-top-cell");
            if (colNum % 4 == 0) {
                topCell.getStyleClass().add("measure-start");
            }
            dynamics.add(topCell, colNum, 0);
            AnchorPane bottomCell = new AnchorPane();
            bottomCell.setPrefSize(scaler.scaleX(Quantizer.COL_WIDTH), 50);
            bottomCell.getStyleClass().add("dynamics-bottom-cell");
            if (colNum % 4 == 0) {
                bottomCell.getStyleClass().add("measure-start");
            }
            dynamics.add(bottomCell, colNum, 1);
        }
        numMeasures++;
    }

    private final TrackNoteCallback noteCallback = new TrackNoteCallback() {
        @Override
        public void setHighlighted(TrackNote note, boolean highlighted) {
            highlighter.clearHighlights();
            if (highlighted) {
                highlighter.addHighlight(note);
            }
        }

        @Override
        public boolean isHighlighted(TrackNote note) {
            return highlighter.isHighlighted(note);
        }

        @Override
        public boolean isInBounds(int rowNum) {
            return rowNum >= 0 && rowNum < 7 * PitchUtils.PITCHES.size();
        }

        @Override
        public String addSongNote(TrackNote note, NoteData toAdd)
                throws NoteAlreadyExistsException {
            int position = toAdd.getPosition();
            if (noteMap.hasNote(position)) {
                throw new NoteAlreadyExistsException();
            } else {
                AddResponse response = model.addNote(toAdd);
                noteMap.putNote(position, note);

                String curPitch = response.getNote().getPitch();
                String prevPitch = curPitch;
                if (response.getPrev().isPresent()) {
                    NeighborData prev = response.getPrev().get();
                    int prevDelta = prev.getDelta();
                    TrackNote prevTrackNote = noteMap.getNote(position - prevDelta);
                    prevTrackNote.adjustForOverlap(prevDelta);
                    prevPitch = PitchUtils.rowNumToPitch(prevTrackNote.getRow());
                    noteMap.putEnvelope(
                            position - prevDelta,
                            prev.getEnvelope(),
                            getEnvelopeCallback(position - prevDelta));
                }
                if (response.getNext().isPresent()) {
                    NeighborData next = response.getNext().get();
                    int nextDelta = next.getDelta();
                    note.adjustForOverlap(nextDelta);
                    noteMap.putEnvelope(
                            position + nextDelta,
                            next.getEnvelope(),
                            getEnvelopeCallback(position + nextDelta));
                    noteMap.putPortamento(
                            position + nextDelta,
                            curPitch,
                            next.getPitchbend(),
                            getPitchbendCallback(position + nextDelta));
                }
                // Add envelope after adjusting note for overlap.
                Optional<EnvelopeData> newEnvelope = response.getNote().getEnvelope();
                if (newEnvelope.isPresent()) {
                    noteMap.putEnvelope(position, newEnvelope.get(), getEnvelopeCallback(position));
                }
                if (response.getNote().getPitchbend().isPresent()) {
                    noteMap.putPortamento(
                            position,
                            prevPitch,
                            response.getNote().getPitchbend().get(),
                            getPitchbendCallback(position));
                }

                // Add measures if necessary.
                if (!response.getNext().isPresent()) {
                    setNumMeasures((position / Quantizer.COL_WIDTH / 4) + 4);
                }

                if (response.getNote().getConfig().isPresent()) {
                    return response.getNote().getConfig().get().getTrueLyric();
                } else {
                    return "";
                }
            }
        }

        @Override
        public void removeSongNote(int position) {
            noteMap.removeFullNote(position);
            RemoveResponse response = model.removeNote(position);
            if (response.getPrev().isPresent()) {
                NeighborData prev = response.getPrev().get();
                int prevDelta = prev.getDelta();
                TrackNote prevTrackNote = noteMap.getNote(position - prevDelta);
                if (response.getNext().isPresent()) {
                    prevTrackNote.adjustForOverlap(prevDelta + response.getNext().get().getDelta());
                } else {
                    prevTrackNote.adjustForOverlap(Integer.MAX_VALUE);
                    // Remove measures until you have 4 measures + previous note.
                    setNumMeasures(((position - prevDelta) / Quantizer.COL_WIDTH / 4) + 4);
                }
                noteMap.putEnvelope(
                        position - prevDelta,
                        prev.getEnvelope(),
                        getEnvelopeCallback(position - prevDelta));
            }
            if (response.getNext().isPresent()) {
                NeighborData next = response.getNext().get();
                int nextDelta = next.getDelta();
                noteMap.putEnvelope(
                        position + nextDelta,
                        next.getEnvelope(),
                        getEnvelopeCallback(position + nextDelta));
                String prevPitch =
                        PitchUtils.rowNumToPitch(noteMap.getNote(position + nextDelta).getRow());
                if (response.getPrev().isPresent()) {
                    prevPitch = PitchUtils.rowNumToPitch(
                            noteMap.getNote(position - response.getPrev().get().getDelta())
                                    .getRow());
                }
                noteMap.putPortamento(
                        position + nextDelta,
                        prevPitch,
                        next.getPitchbend(),
                        getPitchbendCallback(position + nextDelta));
            }

            // Remove all measures if necessary.
            if (noteMap.isEmpty()) {
                setNumMeasures(4);
            }
        }

        @Override
        public void modifySongVibrato(int position) {
            TrackNote toModify = noteMap.getNote(position);
            NoteData mutation = new NoteData(
                    position,
                    toModify.getDuration(),
                    PitchUtils.rowNumToPitch(toModify.getRow()),
                    toModify.getLyric(),
                    noteMap.getPortamento(position).getData(position)
                            .withVibrato(toModify.getVibrato()));
            model.modifyNote(mutation);
        }

        @Override
        public void removeTrackNote(TrackNote trackNote) {
            highlighter.clearHighlights();
            noteMap.removeNoteElement(trackNote);
        }

        @Override
        public Mode getCurrentMode() {
            return model.getCurrentMode();
        }

        @Override
        public Optional<EnvelopeData> getEnvelope(int position) {
            if (noteMap.hasEnvelope(position)) {
                return Optional.of(noteMap.getEnvelope(position).getData());
            }
            return Optional.absent();
        }

        @Override
        public Optional<PitchbendData> getPortamento(int position) {
            if (noteMap.hasPortamento(position)) {
                return Optional.of(noteMap.getPortamento(position).getData(position));
            }
            return Optional.absent();
        }
    };

    private TrackEnvelopeCallback getEnvelopeCallback(final int position) {
        return new TrackEnvelopeCallback() {
            @Override
            public void modifySongEnvelope() {
                TrackNote toModify = noteMap.getNote(position);
                NoteData mutation = new NoteData(
                        toModify.getAbsPosition(),
                        toModify.getDuration(),
                        PitchUtils.rowNumToPitch(toModify.getRow()),
                        toModify.getLyric(),
                        noteMap.getEnvelope(position).getData());
                model.modifyNote(mutation);
            }
        };
    }

    private TrackPortamentoCallback getPitchbendCallback(final int position) {
        return new TrackPortamentoCallback() {
            @Override
            public void modifySongPortamento() {
                TrackNote toModify = noteMap.getNote(position);
                NoteData mutation = new NoteData(
                        position,
                        toModify.getDuration(),
                        PitchUtils.rowNumToPitch(toModify.getRow()),
                        toModify.getLyric(),
                        noteMap.getPortamento(position).getData(position)
                                .withVibrato(toModify.getVibrato()));
                model.modifyNote(mutation);
            }
        };
    }
}
