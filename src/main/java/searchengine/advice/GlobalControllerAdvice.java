package searchengine.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.exceptions.IndexingException;
import searchengine.exceptions.SearchException;

import java.util.Collections;

@ControllerAdvice
public class GlobalControllerAdvice {


    @ExceptionHandler(IndexingException.class)
    public ResponseEntity<IndexingResponse> handleIndexingException(IndexingException e) {
        IndexingResponse response = new IndexingResponse();
        response.setResult(false);
        response.setError(e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(SearchException.class)
    public ResponseEntity<SearchResponse> handleSearchException(SearchException e) {
        SearchResponse response = new SearchResponse();
        response.setCount(0);
        response.setData(Collections.emptyList());
        response.setResult(false);
        response.setError(e.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
