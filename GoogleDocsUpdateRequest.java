package io.github.danthe1st.ide2gdocs.gdocs;

import com.google.api.services.docs.v1.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

class GoogleDocsUpdateRequest {

	private String newContent;
	private int contentOffset;
	private int oldContentLen;//TODO -1...whole until end

	private Consumer<IOException> exceptionHandler;
	private UnaryOperator<DocumentStats> statsModifier;
	public GoogleDocsUpdateRequest(String newContent, int contentOffset, int oldContentLen, Consumer<IOException> exceptionHandler, UnaryOperator<DocumentStats> statsAfterOperation) {
		this.newContent = newContent;
		this.contentOffset = contentOffset;
		this.oldContentLen = oldContentLen;
		this.exceptionHandler = exceptionHandler;
		this.statsModifier = statsAfterOperation;
	}

	public List<Request> buildRequests(int docStartIndex, int allContentLen) {
		int firstIndex = docStartIndex + contentOffset;
		int lastIndex = firstIndex + (oldContentLen==-1?allContentLen:oldContentLen) - 1;
		return buildOverwriteRequests(newContent, firstIndex, lastIndex);
	}

	private static List<Request> buildOverwriteRequests(String newPart, int firstIndex, int lastIndex) {
		List<Request> req = new ArrayList<>();
		if(lastIndex >= firstIndex) {
			req.add(new Request().setDeleteContentRange(
					new DeleteContentRangeRequest().setRange(new Range().setStartIndex(firstIndex).setEndIndex(lastIndex + 1))));
		}
		if(!newPart.isEmpty()) {
			req.add(new Request().setInsertText(new InsertTextRequest().setText(newPart).setLocation(new Location().setIndex(firstIndex))));
		}
		return req;
	}

	public Consumer<IOException> getExceptionHandler() {
		return exceptionHandler;
	}

	public void setExceptionHandler(Consumer<IOException> exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	public DocumentStats getNewStats(DocumentStats oldStats) {
		return statsModifier.apply(oldStats);
	}

}
