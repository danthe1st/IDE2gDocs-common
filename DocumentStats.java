package io.github.danthe1st.ide2gdocs.gdocs;

public class DocumentStats {//TODO fix setter names
	private final int len;
	private final int hashCode;

	public DocumentStats(int len) {
		this.len = len;
		hashCode=-1;
	}

	public DocumentStats(int len, int hashCode) {
		this.len = len;
		this.hashCode = hashCode;
	}

	public DocumentStats incrementLenAndSetHashCode(int lenInc, int hashCode){
		return new DocumentStats(len+lenInc,hashCode);
	}

	public int getLen() {
		return len;
	}

	public int getHashCode() {
		return hashCode;
	}

}
