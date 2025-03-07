package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SearchResponse {
    private Boolean result;
    private String error;
    private Integer count;
    private List<SearchResult> data;
}
