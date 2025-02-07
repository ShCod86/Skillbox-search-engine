package searchengine.dto.indexing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class IndexingResponse {
    private boolean result;
    private String message;

    public IndexingResponse(boolean result) {
        this.result = result;
    }
}
