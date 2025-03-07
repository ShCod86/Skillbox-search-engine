package searchengine.dto.indexing;

import lombok.Data;

@Data
public class IndexingResponse {
    private Boolean result;
    private String error;
}
