import { createBrowserRouter } from "react-router-dom";
import AnimalsPage from "@/pages/AnimalsPage";
import StepsPage from "@/pages/StepsPage";
import AnalysisPage from "@/pages/AnalysisPage";
import PosturePage from "@/pages/PosturePage";
import DataPage from "@/pages/DataPage";
import LocationPage from "@/pages/LocationPage";
import { AppLayout } from "@/components/layout/AppLayout";
import OverviewPage from "@/pages/OverviewPage";

const router = createBrowserRouter([
  {
    path: '/',
    element: <AppLayout />,
    children: [
      {
        index: true,
        element: <OverviewPage />
      },
      {
        path: 'animals',
        element: <AnimalsPage />
      },
      {
        path: 'location',
        element: <StepsPage />
      },
      {
        path: 'posture',
        element: < LocationPage />
      },
      {
        path: 'steps',
        element: <DataPage />
      },
      {
        path: 'data',
        element: <PosturePage />
      },
      {
        path: 'analysis',
        element: <AnalysisPage />
      },
    ]
  }
])

export default router
