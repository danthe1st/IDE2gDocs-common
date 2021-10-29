package io.github.danthe1st.ide2gdocs.gdocs;

import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.services.docs.v1.Docs;
import com.google.api.services.docs.v1.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class GoogleDocsUploader {

	private static final int MAX_QUEUE_SIZE = 10;//TODO make this configurable

	private final BlockingQueue<GoogleDocsUpdateRequest> requestsToProcess = new LinkedBlockingQueue<>();
	private final Thread worker = new Thread(this::work);//TODO stop worker at some point

	private final Docs service;
	private Document doc;
	private int startIndex;
	private DocumentStats currentStats;
	private boolean dirty;

	public GoogleDocsUploader(Docs service) {
		this.service = service;
		worker.setDaemon(true);
		worker.start();
	}

	public static Details getCredentialDetails(String clientId, String clientSecret) {
		Details details = new Details();
		details.setClientId(clientId);
		details.setClientSecret(clientSecret);
		return details;
	}

	public synchronized void setDocument(String id) throws IOException {
		doc = service.documents().get(id).execute();
		startIndex = getLastIndex() - 1;
		currentStats =new DocumentStats(0);
	}

	private synchronized Document getDoc() {
		return doc;
	}

	public synchronized void overwritePart(String newPart, int offset, int oldLen, String fullText, Consumer<IOException> exceptionHandler) {
		if(dirty || requestsToProcess.size() >= MAX_QUEUE_SIZE) {
			overwriteEverything(fullText, exceptionHandler);
		} else {
			int newFullTextHashCode = fullText.hashCode();
			requestsToProcess.add(new GoogleDocsUpdateRequest(newPart,offset,oldLen,exceptionHandler, stats->stats.incrementLenAndSetHashCode(countLengthWithoutWindowsLineBreaks(newPart) - oldLen,newFullTextHashCode)));
		}
	}

	public synchronized void overwriteEverything(String newText, Consumer<IOException> exceptionHandler) {
		requestsToProcess.clear();
		int hashCode = newText.hashCode();
		Consumer<IOException> newExceptionHandler = e -> {
			try {
				doc = service.documents().get(getDoc().getDocumentId()).execute();
			} catch(IOException ex) {
				ex.printStackTrace();
			}
			int docEndIndex = getLastIndex();
			startIndex = Math.min(startIndex, docEndIndex);
			currentStats=new DocumentStats(docEndIndex - startIndex - 1);
			exceptionHandler.accept(e);
		};
		requestsToProcess.add(new GoogleDocsUpdateRequest(newText,0,-1,newExceptionHandler, stats->new DocumentStats(countLengthWithoutWindowsLineBreaks(newText),hashCode)));
		dirty = false;
	}

	private static int countLengthWithoutWindowsLineBreaks(String toCount) {
		char[] chars = toCount.toCharArray();
		char lastChar = '\0';
		int ret = 0;
		for(char c : chars) {
			if(lastChar != '\r' || c != '\n') {
				ret++;
			}
			lastChar = c;
		}
		return ret;
	}

	private int getLastIndex() {
		List<StructuralElement> content = getDoc().getBody().getContent();
		return content.get(content.size() - 1).getEndIndex();
	}

	private void work() {
		while(!Thread.currentThread().isInterrupted()) {
			List<GoogleDocsUpdateRequest> copy = new ArrayList<>();
			try {
				copy.add(requestsToProcess.take());
				requestsToProcess.drainTo(copy);
				List<Request> req = new ArrayList<>();
				DocumentStats stats=currentStats;
				for(GoogleDocsUpdateRequest ur : copy) {
					int allContentLen = stats.getLen();
					req.addAll(ur.buildRequests(startIndex, allContentLen));
					stats = ur.getNewStats(stats);
				}
				if(!req.isEmpty()){
					executeMultiple(req);
				}
				synchronized(this){
					currentStats=stats;
				}
			} catch(InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch(IOException e) {
				synchronized(this) {
					dirty = true;
					copy.get(0).getExceptionHandler().accept(e);
					copy.clear();
				}
			}
		}
	}

	private List<Response> executeMultiple(List<Request> requests) throws IOException {
		BatchUpdateDocumentRequest body = new BatchUpdateDocumentRequest().setRequests(requests);

		BatchUpdateDocumentResponse response = service.documents().batchUpdate(getDoc().getDocumentId(), body).execute();
		return response.getReplies();
	}
}
