package kh.edu.istad.ite.devsoleapi.feature.showcasestep;

/**
 * The two image slots a showcase step carries: the screenshot and the
 * diagram. Keeping them behind one type lets upload and removal be written
 * once rather than duplicated per slot, and per slot again for the published
 * step and its revision.
 */
public enum ShowcaseStepImageKind {

    IMAGE("image") {
        @Override
        public String urlOf(ShowcaseStep step) {
            return step.getImageUrl();
        }

        @Override
        public void setUrl(ShowcaseStep step, String url) {
            step.setImageUrl(url);
        }

        @Override
        public String urlOf(ShowcaseStepRevision step) {
            return step.getImageUrl();
        }

        @Override
        public void setUrl(ShowcaseStepRevision step, String url) {
            step.setImageUrl(url);
        }
    },

    DIAGRAM("diagram") {
        @Override
        public String urlOf(ShowcaseStep step) {
            return step.getDiagramUrl();
        }

        @Override
        public void setUrl(ShowcaseStep step, String url) {
            step.setDiagramUrl(url);
        }

        @Override
        public String urlOf(ShowcaseStepRevision step) {
            return step.getDiagramUrl();
        }

        @Override
        public void setUrl(ShowcaseStepRevision step, String url) {
            step.setDiagramUrl(url);
        }
    };

    private final String storageFolder;

    ShowcaseStepImageKind(String storageFolder) {
        this.storageFolder = storageFolder;
    }

    /** Key namespace segment, so the two slots never collide in the bucket. */
    public String storageFolder() {
        return storageFolder;
    }

    public abstract String urlOf(ShowcaseStep step);

    public abstract void setUrl(ShowcaseStep step, String url);

    public abstract String urlOf(ShowcaseStepRevision step);

    public abstract void setUrl(ShowcaseStepRevision step, String url);
}
