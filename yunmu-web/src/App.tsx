import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import OverviewPage  from './pages/OverviewPage';
import AnimalsPage   from './pages/AnimalsPage';
import LocationPage  from './pages/LocationPage';
import PosturePage   from './pages/PosturePage';
import StepsPage     from './pages/StepsPage';
import DataPage      from './pages/DataPage';
import AnalysisPage  from './pages/AnalysisPage';

export default function App() {
  return (
    <BrowserRouter>
      <AppLayout>
        <Routes>
          <Route path="/"         element={<OverviewPage  />} />
          <Route path="/animals"  element={<AnimalsPage   />} />
          <Route path="/location" element={<LocationPage  />} />
          <Route path="/posture"  element={<PosturePage   />} />
          <Route path="/steps"    element={<StepsPage     />} />
          <Route path="/data"     element={<DataPage      />} />
          <Route path="/analysis" element={<AnalysisPage  />} />
        </Routes>
      </AppLayout>
    </BrowserRouter>
  );
}
